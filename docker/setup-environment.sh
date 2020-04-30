export DOCKER_IP=172.17.0.1
docker-compose up &&
#${KAFKA_HOME}/bin/kafka-topics.sh --create --zookeeper localhost:2181 --topic place-order-events --partitions 1 --replication-factor 1
/home/lglo/projects/kafka-docker/create-topics.sh --zookeeper localhost:2181 --topic place-order-events --partitions 1 --replication-factor 1