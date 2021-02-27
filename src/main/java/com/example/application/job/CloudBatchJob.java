package com.example.application.job;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/*This job is launched via runJob() and performs the following steps:
 * 1. Checks AWS S3 bucket for the presence of ZIP Files
 * 2. If files are found on S3 bucket,
 * 3. For the oldest zip file, unzip its contents & transfer resulting csv files to destination GCS Bucket.
 * 4. Repeat above steps after a scheduled interval 0f 5 minutes (configured through @Scheduled)
 *  */

@Component
@Slf4j
public class CloudBatchJob {

    public static final String ZIP_FILE_SUFFIX = "zip";
    private final AmazonS3 amazonS3;
    private final Storage storage;
    private final String awsBucket;
    private final String awsTransferFromLocation;
    private final String gcsBucket;
    private final String gcsFolder;

    public CloudBatchJob(AmazonS3 amazonS3, Storage storage, @Value("${cloud.batch.source.aws.bucket}") String awsBucket,
                         @Value("${cloud.batch.source.aws.location}") String awsTransferFromLocation, @Value("${cloud.batch.destination.gcs.bucket}") String gcsBucket,
                         @Value("${cloud.batch.destination.gcs.location}") String gcsFolder) {
        this.amazonS3 = amazonS3;
        this.storage = storage;
        this.awsBucket = awsBucket;
        this.awsTransferFromLocation = awsTransferFromLocation;
        this.gcsBucket = gcsBucket;
        this.gcsFolder = gcsFolder;
    }

    @Scheduled(fixedDelay = 300000)
    private void runJob() {
        try {
            transferFilefromS3toGcs();
        } catch (IOException | GeneralSecurityException exception) {
            log.info(exception.getLocalizedMessage());
        }

    }

    private void transferFilefromS3toGcs() throws GeneralSecurityException, IOException {
        ObjectListing ol = amazonS3.listObjects(awsBucket, awsTransferFromLocation);
        List<S3ObjectSummary> summaries = ol.getObjectSummaries();
        Date current = new Date();
        String oldestKey = null;
        for (S3ObjectSummary summary : summaries) {
            if (summary.getKey().endsWith(ZIP_FILE_SUFFIX)) {
                Date prev = summary.getLastModified();
                if (prev.before(current)) {
                    oldestKey = summary.getKey();
                    current = prev;
                }
            }
        }
        if (oldestKey != null) {
            S3Object s3Object = amazonS3.getObject(new GetObjectRequest(awsBucket, oldestKey));
            log.info("Transferring S3 File {} from bucket {}", oldestKey, awsBucket);
            ZipInputStream zis = new ZipInputStream(s3Object.getObjectContent());
            ZipEntry entry;
            StringBuilder destinationFolder = new StringBuilder(gcsFolder).append("data_files_").append(new SimpleDateFormat("yyyy-MM-dd-HHmm").format(new Date())).append("/");
            while ((entry = zis.getNextEntry()) != null) {
                writeFiletoGCS(destinationFolder.toString().concat(entry.getName()), zis);
            }
            log.info("Removing S3 File {} from bucket {}", oldestKey, awsBucket);
            amazonS3.deleteObject(awsBucket, oldestKey);
        }

    }

    private void writeFiletoGCS(String objectName, InputStream is) throws IOException {
        BlobId blobId = BlobId.of(gcsBucket, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        log.info("Creating GCS File {} on bucket {}", objectName, gcsBucket);
        storage.createFrom(blobInfo, is);
    }
}
