
Usage 

# Maven S3 Wagon

This wagon enables communication between Maven and any S3 provider overriding the S3 endpoint.

pom's with a reference to this wagon can publish build artifacts (.jar's, .war's, etc) to S3.

This project is based on https://github.com/jcaddel/maven-s3-wagon/
Inspired from https://github.com/Kazzer/wagon-s3/tree/main

# Documentation

## Configure Maven to use the wagon
Configuring Maven to use the wagon is very simple.
You will need to follow these steps:
* Provide configuration
* Add the wagon to the build section of a pom
* Add the distribution management section to the pom

### Provide configuration
You will need to provide the following information to authenticate with your S3 provider:
* From one of your S3 provider API key: an Access Key ID + a Secret Access Key
* From your S3 bucket:
    * Region: `fr-par`
    * Endpoint: `s3.fr-par.scw.cloud`
    * Bucket Name

_Values are examples, you will need to replace them with your own values._

When you will run `mvn deploy`, configuration properties are resolved by using the following order.
* Environment Variables: `AWS_ACCESS_KEY_ID` , `AWS_SECRET_KEY`, `AWS_REGION`, `AWS_S3_ENDPOINT`, `MAVEN_S3_ACL`
* !!TODO!! System Properties: `aws.accessKeyId`, `aws.secretKey`, `aws.region`, `aws.s3.endpoint`, `maven.s3.acl`
* Properties in the maven `settings.xml` file, for an example:
```xml
    <server>
      <id>s3.maven.ssk-it.fr</id>
      <configuration>
        <s3Configuration>
          <region>fr-par</region>
          <endpointOverride>https://s3.fr-par.scw.cloud</endpointOverride>
          <accessKeyId>SCWXAVSF19KMCJV1FY0G</accessKeyId>
          <secretAccessKey>b3d01d4b-23f5-4ee7-a5fd-03a1c310e736</secretAccessKey>
        </s3Configuration>
      </configuration>
    </server> 
```

_When a property is not defined for a strategy, the next strategy is used._  

The `MAVEN_S3_ACL` is optional and can be set to one of the following values:
* `private`
* `public-read`
* `public-read-write`
* `authenticated-read`
* `aws-exec-read`
* `bucket-owner-read`
* `bucket-owner-full-control`
  
### Add the wagon to the build section of a pom
Add this to the build section of a pom:
```xml
    <build>
      <extensions>
        <extension>
          <groupId>fr.sskit.maven.wagon</groupId>
          <artifactId>wagon-s3</artifactId>
          <version>1.0.0</version>
       </extension>
      </extensions>
    </build>
```

### Add the distribution management section to the pom
```xml
    <distributionManagement>
      <site>
        <id>s3.site</id>
        <url>s3://[S3 Bucket Name]/site</url>
      </site>
      <repository>
        <id>s3.release</id>
        <url>s3://[S3 Bucket Name]/release</url>
      </repository>
      <snapshotRepository>
        <id>s3.snapshot</id>
        <url>s3://[S3 Bucket Name]/snapshot</url>
      </snapshotRepository>
    </distributionManagement>
```



If things are setup correctly, `$ mvn deploy` will produce output similar to this:

    [INFO] --- maven-deploy-plugin:2.7:deploy (default-deploy) @ kuali-example ---
    [INFO] Logged in - maven.kuali.org
    Uploading: s3://maven.kuali.org/release/org/kuali/common/kuali-example/1.0.0/kuali-example-1.0.0.jar
    [INFO] Logged off - maven.kuali.org
    [INFO] Transfers: 1 Time: 2.921s Amount: 7.6M Throughput: 2.6 MB/s
    [INFO] ------------------------------------------------------------------------
    [INFO] BUILD SUCCESS
    [INFO] ------------------------------------------------------------------------


