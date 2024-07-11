package fr.sskit.maven.wagon.providers.s3;

import static java.util.Optional.ofNullable;
import static software.amazon.awssdk.regions.Region.of;

import java.io.File;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import org.apache.maven.wagon.AbstractWagon;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.resource.Resource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// CHECKSTYLE.OFF: ClassDataAbstractionCoupling
public final class S3Wagon extends AbstractWagon {
  // CHECKSTYLE.ON: ClassDataAbstractionCoupling
  private S3Client s3;
  private String bucket;
  private String prefix;
  private ObjectCannedACL acl;

  public S3WagonConfiguration s3Configuration;

  @Override
  protected void closeConnection() {
    fireSessionLoggedOff();

    if (s3 != null) {
      s3.close();
    }
  }

  @Override
  public void get(final String resourceName, final File destination)
      throws TransferFailedException, ResourceDoesNotExistException {
    final Resource resource = new Resource(resourceName);

    fireGetInitiated(resource, destination);

    getObject(resource, destination);
  }

  @Override
  public boolean getIfNewer(final String resourceName, final File destination, final long timestamp)
      throws TransferFailedException, ResourceDoesNotExistException {
    final Resource resource = new Resource(resourceName);

    fireGetInitiated(resource, destination);

    final HeadObjectResponse metadata = s3.headObject(HeadObjectRequest
        .builder()
        .bucket(bucket)
        .key(String.join("", prefix, resourceName))
        .build());
    final boolean destinationIsNewer = metadata.lastModified()
        .isAfter(Instant.ofEpochMilli(timestamp));
    if (destinationIsNewer) {
      getObject(resource, destination);
    }

    return destinationIsNewer;
  }

  @Override
  protected void openConnectionInternal() throws ConnectionException, AuthenticationException {
    bucket = getRepository().getHost();

    var credentialsProvider = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(
            ofNullable(System.getenv("AWS_ACCESS_KEY_ID")).orElse(s3Configuration.getAccessKeyId()),
            ofNullable(System.getenv("AWS_SECRET_KEY")).orElse(s3Configuration.getSecretAccessKey())
        )
    );
    var s3Builder = S3Client
        .builder()
        .credentialsProvider(credentialsProvider);
    var region = of(ofNullable(System.getenv("AWS_REGION")).orElse(s3Configuration.getRegion()));
    if (region != null && region.id() != null && !region.id().isEmpty()) {
      s3Builder = s3Builder.region(region);
    }
    var endpointOverride = ofNullable(System.getenv("AWS_S3_ENDPOINT")).orElse(
        s3Configuration.getEndpointOverride());
    if (endpointOverride != null && !endpointOverride.isEmpty()) {
      s3Builder = s3Builder.endpointOverride(URI.create(endpointOverride));
    }
    s3 = s3Builder.build();

    fireSessionLoggedIn();

    try {
      s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    } catch (final NoSuchBucketException nsbe) {
      throw new ConnectionException(String.format("Bucket '%s' does not exist", bucket));
    } catch (final AwsServiceException ase) {
      throw new AuthenticationException(
          String.format("Insufficient permissions to access bucket '%s'", bucket),
          ase);
    }

    acl = Arrays
        .stream(ObjectCannedACL.values())
        .filter(cacl -> cacl.toString().equals(System.getenv("MAVEN_S3_ACL")))
        .findFirst()
        .orElse(null);
    prefix = getPrefix(getRepository().getBasedir());
  }

  @Override
  public void put(final File source, final String destination) throws TransferFailedException {
    final Resource resource = new Resource(destination);

    firePutInitiated(resource, source);

    final String key;
    key = String.join("", prefix, destination);

    try {
      var upload = s3.putObject(PutObjectRequest.builder()
              .acl(acl)
              .bucket(bucket)
              .contentLength(source.length())
              .key(key)
              .build(),
          RequestBody.fromFile(source));
      if (upload == null) {
        throw AwsServiceException.builder().message("Null put object response").build();
      }
    } catch (final AwsServiceException ase) {
      fireTransferError(resource, ase, TransferEvent.REQUEST_PUT);
      throw new TransferFailedException(String.format("Unable to upload '%s'", source.getName()),
          ase);
    }

    postProcessListeners(resource, source, TransferEvent.REQUEST_PUT);

    firePutCompleted(resource, source);
  }

  private void getObject(final Resource source, final File destination)
      throws TransferFailedException, ResourceDoesNotExistException {
    fireGetStarted(source, destination);

    final String key = String.join("", prefix, source.getName());

    try {
      s3.headObject(HeadObjectRequest
          .builder()
          .bucket(bucket)
          .key(key)
          .build());
    } catch (final NoSuchKeyException nske) {
      fireTransferError(source, null, TransferEvent.REQUEST_GET);

      throw new ResourceDoesNotExistException(String.format(
          "Resource '%s' does not exist in bucket '%s'",
          source.getName(),
          bucket));
    }

    try {
      var download = s3.getObject(GetObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .build(),
          destination.toPath());
      if (download == null) {
        throw AwsServiceException.builder().message("Null get object response").build();
      }
    } catch (final AwsServiceException ase) {
      fireTransferError(source, ase, TransferEvent.REQUEST_GET);

      throw new TransferFailedException(String.format("Unable to download '%s'", source.getName()),
          ase);
    }

    postProcessListeners(source, destination, TransferEvent.REQUEST_GET);

    fireGetCompleted(source, destination);
  }

  private static String getPrefix(final String baseDir) {
    final String separator = "/";
    final String prefix = baseDir.startsWith(separator) ? baseDir.substring(1) : baseDir;
    return prefix.endsWith(separator) ? prefix : String.format("%s/", prefix);
  }
}