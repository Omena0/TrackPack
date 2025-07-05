import zipfile
import hashlib
import uuid
import json

# This script generates dummy resource packs and output packs.json.
# Please copy packs.json into run/plugins/TrackPack/packs.json.

# NOTE: Put the http server URL in here
SERVER_URL = "http://127.0.0.1:8000"

packs = []

for i in range(0, 24):
    with zipfile.ZipFile(f"packs/{i}", mode="w") as zf:
        zf.writestr(
            "pack.mcmeta",
            '{"pack":{"pack_format":22,"supported_formats":[22,1000],"description":"pack '
            + str(i)
            + '"}}',
        )

    with open(f"packs/{i}", "rb") as f:
        hash = hashlib.file_digest(f, "sha1").hexdigest()

    packs.append({"url": f"{SERVER_URL}/packs/{i}", "uuid": str(uuid.uuid4()), "hash": hash})

with open("packs.json", "w") as f:
    json.dump(packs, f, indent=4)
