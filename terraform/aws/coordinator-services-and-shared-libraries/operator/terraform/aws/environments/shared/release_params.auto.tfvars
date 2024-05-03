# Generated from release version - using prebuilt AMI
# If you want use your self-built AMI, follow the steps at
# https://github.com/privacysandbox/aggregation-service/blob/main/docs/aws-aggregation-service.md#set-up-your-deployment-environment
# and change ami_owners to ["self"]
ami_name = "aggregation-service-enclave_2.4.2"
ami_owners = ["971056657085"]

change_handler_lambda = "../../jars/AwsChangeHandlerLambda_2.4.2.jar"
frontend_lambda = "../../jars/AwsApiGatewayFrontend_2.4.2.jar"
sqs_write_failure_cleanup_lambda = "../../jars/AwsFrontendCleanupLambda_2.4.2.jar"
asg_capacity_handler_lambda = "../../jars/AsgCapacityHandlerLambda_2.4.2.jar"
terminated_instance_handler_lambda = "../../jars/TerminatedInstanceHandlerLambda_2.4.2.jar"

