#!/bin/bash
# Initialise LocalStack SQS FIFO queue on startup
awslocal sqs create-queue \
  --queue-name heartbeat-fallback.fifo \
  --attributes FifoQueue=true,ContentBasedDeduplication=false \
  --region us-east-1
echo "SQS FIFO queue created"
