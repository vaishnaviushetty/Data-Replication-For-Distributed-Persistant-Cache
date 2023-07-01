FROM openjdk:17
EXPOSE 8080
ADD target/partitionexample-0.0.1-SNAPSHOT.jar partition-restapi-docker.jar
ENTRYPOINT ["java","-jar","partition-restapi-docker.jar"]