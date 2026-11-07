# BuildBuddy's Ubuntu 24.04 RBE image: index gcr.io/flame-public/rbe-ubuntu24-04:2024-04-17-latest
# (sha256:f7db0d4791247f032fdb4451b7c3ba90e567923a341cc6dc43abfc283436791a) holds one build per executor architecture;
# each platform pins its own manifest digest from that index.
RBE_IMAGE = "docker://gcr.io/flame-public/rbe-ubuntu24-04@sha256:5d2d8b3935c4dad4d81e0b6d89eeb06734d0e708cdf3d8ec779cf619c9243860"

RBE_IMAGE_ARM64 = "docker://gcr.io/flame-public/rbe-ubuntu24-04@sha256:01898e8731f34ab4104c956909cb3b3020e630e9146c2cbbef145d8155febdf4"
