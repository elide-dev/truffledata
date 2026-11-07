#!/bin/bash
# Build and run the Java probe. Usage: jprobe.sh bench <iters> files... | jprobe.sh events-batch <list> <outdir>
set -e
S=/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad
J=$S/dl/jars
CP=$J/snakeyaml-engine-3.2.jar:$J/snakeyaml-2.7.jar:$J/jackson-dataformat-yaml-2.22.4.jar:$J/jackson-core-2.22.4.jar:$J/jackson-databind-2.22.4.jar:$J/jackson-annotations-2.22.jar
mkdir -p $S/build/jprobe
if [ ! -f $S/build/jprobe/JProbe.class ] || [ $S/jprobe/JProbe.java -nt $S/build/jprobe/JProbe.class ]; then
  javac -d $S/build/jprobe -cp $CP $S/jprobe/JProbe.java
fi
cd $S
java -Xmx2g -cp $S/build/jprobe:$CP JProbe "$@"
