package fr.sskit.maven.wagon.providers.s3;

import lombok.Data;

@Data
public class S3WagonConfiguration {
  String region;
  String endpointOverride;
  String accessKeyId;
  String secretAccessKey;
}
