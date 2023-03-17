# GeoSensorX Custom TB API Service

## Prerequisites

- [Install Docker CE](https://docs.docker.com/engine/installation/)

## Running

In order to start the gsx-api-microservice first create a plain text file to set up test configuration (in our example configuration file name is *.env*):
```bash
touch .env
```

Edit this *.env* file:
```bash
nano .env
```

and put next content into the text file (modify it according to your test goals):
```bash
REST_URL=IP_ADDRESS_OF_TB_INSTANCE
# IP_ADDRESS_OF_TB_INSTANCE is your local IP address if you run ThingsBoard on your dev machine in docker
REST_SSL=true
REST_READ_TIMEOUT=0
```

Where: 
    
- `REST_URL`                     - Rest URL of the TB instance
- `REST_SSL`                     - Rest SSL option(REST_URL should be https://)
- `REST_READ_TIMEOUT`            - URLConnection's read timeout(A timeout value of 0 specifies an infinite timeout.)


Before starting Docker container run following commands to create a directory for storing logs and then change its owner to docker container user, to be able to change user, chown command is used, which requires sudo permissions (command will request password for a sudo access):
```bash
mkdir -p ~/.gsx-api-microservice-logs && sudo chown -R 799:799 ~/.gsx-api-microservice-logs
```

Once params are configured to run application simple type from the folder where configuration file is located:
```bash
docker run -it -d -p 9095:9094/tcp --env-file .env -v ~/.gsx-api-microservice-logs:/var/log/geosensorx-custom-tb-service --name gsx-api-microservice --restart always gsxcloudapi/gsx-api-microservice
```
