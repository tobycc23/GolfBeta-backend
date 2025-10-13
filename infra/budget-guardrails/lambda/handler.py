# lambda/handler.py
import os
import json
import boto3

EC2 = boto3.client("ec2")
RDS = boto3.client("rds")

TAG_KEY = os.environ.get("PROJECT_TAG_KEY", "budget-protect")
TAG_VAL = os.environ.get("PROJECT_TAG_VALUE", "true")

def stop_tagged_ec2():
    to_stop = []
    paginator = EC2.get_paginator("describe_instances")
    for page in paginator.paginate(
        Filters=[{"Name":"tag:"+TAG_KEY, "Values":[TAG_VAL]},
                 {"Name":"instance-state-name", "Values":["pending","running","stopping","stopped"]}]
    ):
        for r in page.get("Reservations", []):
            for i in r.get("Instances", []):
                if i["State"]["Name"] in ("running","pending"):
                    to_stop.append(i["InstanceId"])
    if to_stop:
        EC2.stop_instances(InstanceIds=to_stop)

def stop_tagged_rds():
    paginator = RDS.get_paginator("describe_db_instances")
    for page in paginator.paginate():
        for db in page.get("DBInstances", []):
            tags = RDS.list_tags_for_resource(ResourceName=db["DBInstanceArn"]).get("TagList", [])
            tag_map = {t["Key"]: t["Value"] for t in tags}
            if tag_map.get(TAG_KEY) == TAG_VAL:
                # Only try to stop if engine/az supports it and it's available
                if db["DBInstanceStatus"] == "available":
                    try:
                        RDS.stop_db_instance(DBInstanceIdentifier=db["DBInstanceIdentifier"])
                    except Exception as e:
                        # Some engines/classes can’t be stopped (e.g. Multi-AZ or older gens) — swallow gracefully
                        print(f"Skip stopping {db['DBInstanceIdentifier']}: {e}")

def handler(event, context):
    # Budget/SNS messages arrive here; we act unconditionally when invoked
    print("Event:", json.dumps(event))
    stop_tagged_ec2()
    stop_tagged_rds()
    return {"ok": True}
