#!/usr/bin/env python3
"""Sign a checked repository and publish via Sonatype's Central Publisher API.

Protocol: https://central.sonatype.org/publish/publish-portal-api/
Upload is USER_MANAGED. Publication is a separate explicit command after validation.
Credentials are read only from SONATYPE_USERNAME / SONATYPE_TOKEN; signing uses
SIGNING_KEY_ID and an already unlocked GPG key. Secrets never enter command lines.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile

from maven_repo import MODULES, NAMESPACE, artifact_suffixes, validate_version

API = 'https://central.sonatype.com/api/v1/publisher/'
HASHES = ('md5', 'sha1', 'sha256', 'sha512')


def checked_payloads(repository, version):
    validate_version(version)
    if 'SNAPSHOT' in version.upper():
        raise ValueError('Central releases must be immutable versions')
    files = []
    for module in MODULES:
        artifact = 'truffledata-' + module
        directory = repository / 'dev/elide' / artifact / version
        prefix = artifact + '-' + version
        pom = ET.parse(directory / (prefix + '.pom')).getroot()
        for key, expected in [('groupId', 'dev.elide'), ('artifactId', artifact), ('version', version)]:
            if pom.findtext('m:' + key, namespaces=NAMESPACE) != expected:
                raise ValueError('Incorrect Central POM coordinates: ' + module)
        for element in ('name', 'description', 'url', 'licenses/license/name', 'developers/developer/name', 'scm/connection', 'scm/url'):
            if not pom.findtext('/'.join('m:' + p for p in element.split('/')), namespaces=NAMESPACE):
                raise ValueError('Missing Central POM metadata: ' + module + '/' + element)
        for suffix in artifact_suffixes(module):
            path = directory / (prefix + suffix)
            if not path.is_file():
                raise ValueError('Missing release attachment: ' + str(path))
        for path in sorted(directory.iterdir()):
            if path.suffix.lstrip('.') in HASHES or path.suffix == '.asc':
                continue
            if not path.is_file() or path.is_symlink() or not path.name.startswith(prefix + '.') and not path.name.startswith(prefix + '-'):
                raise ValueError('Unexpected release entry: ' + str(path))
            data = path.read_bytes()
            for algorithm in HASHES:
                checksum = Path(str(path) + '.' + algorithm)
                if checksum.read_text().strip() != hashlib.new(algorithm, data).hexdigest():
                    raise ValueError('Checksum mismatch: ' + str(path))
            files.append(path)
    return files


def sign(repository, version):
    key = os.environ.get('SIGNING_KEY_ID')
    if not key:
        raise ValueError('SIGNING_KEY_ID is required')
    payloads = checked_payloads(repository, version)
    for path in payloads:
        signature = Path(str(path) + '.asc')
        if not signature.exists():
            subprocess.run(['gpg', '--batch', '--pinentry-mode', 'error', '--local-user', key,
                            '--armor', '--detach-sign', '--output', str(signature), str(path)], check=True)
        subprocess.run(['gpg', '--batch', '--verify', str(signature), str(path)], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print('Verified signatures for %d release payloads' % len(payloads))


def make_bundle(repository, version, output):
    payloads = checked_payloads(repository, version)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(str(output) + '.tmp')
    try:
        with zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            for payload in payloads:
                for path in [payload] + [Path(str(payload) + '.' + suffix) for suffix in HASHES + ('asc',)]:
                    if not path.is_file():
                        raise ValueError('Missing signed attachment: ' + str(path))
                    if path.suffix == '.asc':
                        subprocess.run(['gpg', '--batch', '--verify', str(path), str(payload)], check=True,
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                    entry = zipfile.ZipInfo(path.relative_to(repository).as_posix(), (1980, 1, 1, 0, 0, 0))
                    entry.compress_type = zipfile.ZIP_DEFLATED
                    entry.external_attr = 0o100644 << 16
                    archive.writestr(entry, path.read_bytes())
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)
    print(output)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError('Central unexpectedly redirected an authenticated request')


def read_credentials(path):
    """Read a private JSON pair, Maven <server> fragment, or full settings.xml."""
    text = Path(path).read_text()
    if text.lstrip().startswith('{'):
        return json.loads(text)
    root = ET.fromstring(text)
    for node in root.iter():
        node.tag = node.tag.split('}')[-1]
    servers = [root] if root.tag == 'server' else root.findall('.//servers/server')
    requested = os.environ.get('CENTRAL_SERVER_ID')
    if requested:
        servers = [s for s in servers if s.findtext('id') == requested]
    if len(servers) != 1:
        raise ValueError('Set CENTRAL_SERVER_ID to select one publishing server')
    def resolve(value):
        return re.sub(r'\$\{env\.([^}]+)\}', lambda m: os.environ[m[1]], value or '')
    return {'username': resolve(servers[0].findtext('username')), 'token': resolve(servers[0].findtext('password'))}


def request(path, data=b'', content_type='application/json'):
    credential_file = os.environ.get('CENTRAL_CREDENTIALS_FILE')
    credentials = read_credentials(credential_file) if credential_file else {}
    username = credentials.get('username') or os.environ.get('SONATYPE_USERNAME')
    password = credentials.get('token') or os.environ.get('SONATYPE_TOKEN')
    if not username or not password:
        raise ValueError('SONATYPE_USERNAME and SONATYPE_TOKEN are required')
    token = base64.b64encode((username + ':' + password).encode()).decode()
    req = urllib.request.Request(API + path, data=data, method='POST', headers={
        'Authorization': 'Bearer ' + token, 'Content-Type': content_type,
    })
    try:
        with urllib.request.build_opener(NoRedirect).open(req, timeout=60) as response:
            return response.read().decode()
    except urllib.error.HTTPError as error:
        # Do not log response headers or a response that could echo credentials.
        raise RuntimeError('Central request failed with HTTP %d' % error.code) from None


def deployment_id(value):
    if not re.fullmatch(r'[0-9a-fA-F-]{36}', value) or str(uuid.UUID(value)) != value.lower():
        raise ValueError('Invalid Central deployment ID')
    return value


def upload(bundle, state):
    if state.exists():
        raise ValueError('Deployment state already exists; use status or publish to resume it')
    boundary = 'truffledata-' + uuid.uuid4().hex
    body = ('--' + boundary + '\r\nContent-Disposition: form-data; name="bundle"; filename="central-bundle.zip"\r\n'
            'Content-Type: application/octet-stream\r\n\r\n').encode() + bundle.read_bytes() + ('\r\n--' + boundary + '--\r\n').encode()
    identifier = deployment_id(request('upload?publishingType=USER_MANAGED&name=' + urllib.parse.quote(bundle.name),
                                       body, 'multipart/form-data; boundary=' + boundary).strip())
    print('Uploaded Central deployment ' + identifier, flush=True)
    state.write_text(json.dumps({'deploymentId': identifier, 'bundleSha256': hashlib.sha256(bundle.read_bytes()).hexdigest()}, indent=2) + '\n')


def status(identifier):
    result = json.loads(request('status?id=' + deployment_id(identifier)))
    # Only report documented status fields, never request or auth headers.
    print(json.dumps({key: result[key] for key in ('deploymentId', 'deploymentState', 'errors') if key in result}, indent=2), flush=True)
    return result['deploymentState']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['sign', 'bundle', 'upload', 'status', 'wait', 'publish'])
    parser.add_argument('--repository', type=Path)
    parser.add_argument('--version')
    parser.add_argument('--bundle', type=Path)
    parser.add_argument('--state', type=Path)
    parser.add_argument('--until', choices=['VALIDATED', 'PUBLISHED'], default='VALIDATED')
    parser.add_argument('--timeout', type=int, default=1200)
    args = parser.parse_args()
    if args.action in ('sign', 'bundle'):
        if args.repository is None or args.version is None:
            parser.error('--repository and --version are required')
        if args.action == 'sign':
            sign(args.repository, args.version)
        elif args.bundle is None:
            parser.error('--bundle is required')
        else:
            make_bundle(args.repository, args.version, args.bundle)
    elif args.state is None:
        parser.error('--state is required')
    elif args.action == 'upload':
        if args.bundle is None:
            parser.error('--bundle is required')
        upload(args.bundle, args.state)
    else:
        identifier = deployment_id(json.loads(args.state.read_text())['deploymentId'])
        current = status(identifier)
        if args.action == 'wait':
            deadline = time.monotonic() + args.timeout
            while current != args.until:
                if current == 'FAILED' or time.monotonic() >= deadline:
                    raise RuntimeError('Central did not reach ' + args.until + '; resume with the recorded deployment state')
                time.sleep(10)
                current = status(identifier)
        if args.action == 'publish':
            if current == 'VALIDATED':
                request('deployment/' + identifier)
                print('Central publication started')
            elif current not in ('PUBLISHING', 'PUBLISHED'):
                raise ValueError('Central deployment must be VALIDATED before publication')


if __name__ == '__main__':
    main()
