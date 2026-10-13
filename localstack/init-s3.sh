#!/bin/sh
# Бакет для фото. LocalStack запускает скрипты из ready.d, когда S3 уже поднят.
# CORS нужен браузеру: страница с другого адреса грузит файл PUT-ом прямо в хранилище
set -e
awslocal s3api head-bucket --bucket tachkiosk-photos 2>/dev/null || awslocal s3 mb s3://tachkiosk-photos
awslocal s3api put-bucket-cors --bucket tachkiosk-photos --cors-configuration '{
  "CORSRules": [{
    "AllowedOrigins": ["*"],
    "AllowedMethods": ["PUT", "GET"],
    "AllowedHeaders": ["*"],
    "MaxAgeSeconds": 600
  }]
}'
