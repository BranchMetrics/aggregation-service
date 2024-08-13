/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.aggregate.adtech.worker;

import static com.google.aggregate.adtech.worker.AwsWorkerContinuousTestHelper.AWS_S3_BUCKET_REGION;
import static com.google.aggregate.adtech.worker.AwsWorkerContinuousTestHelper.getOutputFileName;
import static com.google.aggregate.adtech.worker.AwsWorkerContinuousTestHelper.getS3Bucket;
import static com.google.aggregate.adtech.worker.AwsWorkerContinuousTestHelper.getS3Key;
import static com.google.aggregate.adtech.worker.AwsWorkerContinuousTestHelper.readDebugResultsFromS3;
import static com.google.aggregate.adtech.worker.AwsWorkerContinuousTestHelper.readResultsFromS3;
import static com.google.aggregate.adtech.worker.AwsWorkerContinuousTestHelper.submitJobAndWaitForResult;
import static com.google.aggregate.adtech.worker.util.DebugSupportHelper.getDebugFilePrefix;
import static com.google.common.truth.Truth.assertThat;
import static com.google.scp.operator.protos.frontend.api.v1.ReturnCodeProto.ReturnCode.SUCCESS;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.acai.Acai;
import com.google.aggregate.adtech.worker.model.AggregatedFact;
import com.google.aggregate.adtech.worker.testing.AvroResultsFileReader;
import com.google.aggregate.protocol.avro.AvroDebugResultsReaderFactory;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.scp.operator.cpio.blobstorageclient.aws.S3BlobStorageClient;
import com.google.scp.operator.cpio.blobstorageclient.aws.S3BlobStorageClientModule.PartialRequestBufferSize;
import com.google.scp.operator.cpio.blobstorageclient.aws.S3BlobStorageClientModule.S3UsePartialRequests;
import com.google.scp.operator.protos.frontend.api.v1.CreateJobRequestProto.CreateJobRequest;
import java.math.BigInteger;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Integration test which runs against an AWS deployment and verifies that a job accessing an
 * existing encrypted payload can be processed by the system and produce an output avro file.
 *
 * <p>This test is used to ensure API compatibility with Chrome. The input file contains reports
 * that were generated by Chrome.
 */
@RunWith(JUnit4.class)
public class AwsWorkerContinuousSmokeTestChromeReports {

  @Rule public final Acai acai = new Acai(TestEnv.class);
  @Rule public final TestName name = new TestName();

  private static final Duration COMPLETION_TIMEOUT = Duration.of(10, ChronoUnit.MINUTES);

  // Input data contains reports generated by Chrome. Use the GenerateInputs utility to write avro
  // files of reports:
  // bazel run //java/com/google/aggregate/tools/generateinputs:GenerateInputs -- \
  //   --reports_dir <reports-dir> \
  //   --output_dir <output-dir>
  private static String INPUT_DATA_URI = System.getenv("INPUT_DATA_URI");

  // Output domain contains the buckets in the reports
  private static String OUPUT_DOMAIN_URI = System.getenv("OUPUT_DOMAIN_URI");

  private static String OUTPUT_DATA_URI = System.getenv("OUTPUT_DATA_URI");

  @Inject S3BlobStorageClient s3BlobStorageClient;
  @Inject AvroResultsFileReader avroResultsFileReader;
  @Inject private AvroDebugResultsReaderFactory readerFactory;

  /**
   * Creates a job via the frontend API, waits for the job to be processed, and checks the result.
   */
  @Test
  public void createJobE2ETest() throws Exception {
    // Inputs
    String inputBucket = getS3Bucket(INPUT_DATA_URI);
    String inputKey = getS3Key(INPUT_DATA_URI);
    String outputDomainBucket = getS3Bucket(OUPUT_DOMAIN_URI);
    String outputDomainKey = getS3Key(OUPUT_DOMAIN_URI);

    // Outputs
    String outputBucket = getS3Bucket(OUTPUT_DATA_URI);
    String outputKey = getS3Key(OUTPUT_DATA_URI);

    // Create the job and wait for the result
    CreateJobRequest createJobRequest =
        AwsWorkerContinuousTestHelper.createJobRequestWithAttributionReportTo(
            inputBucket,
            inputKey,
            outputBucket,
            outputKey,
            /* jobId= */ getClass().getSimpleName() + "::" + name.getMethodName(),
            Optional.of(outputDomainBucket),
            Optional.of(outputDomainKey));
    JsonNode result = submitJobAndWaitForResult(createJobRequest, COMPLETION_TIMEOUT);

    assertThat(result.get("result_info").get("return_code").asText()).isEqualTo(SUCCESS.name());

    // Read output avro from s3.
    ImmutableList<AggregatedFact> aggregatedFacts =
        readResultsFromS3(s3BlobStorageClient, avroResultsFileReader, outputBucket, outputKey);

    // NOTE: this result assertion assumes constant noising, if run with a worker using other
    // noising then this will fail.
    assertThat(aggregatedFacts)
        .containsExactly(
            AggregatedFact.create(BigInteger.valueOf(0), 345),
            AggregatedFact.create(BigInteger.valueOf(1).shiftLeft(120) /* = 2^120 */, 2),
            AggregatedFact.create(BigInteger.valueOf(1234), 505),
            AggregatedFact.create(BigInteger.valueOf(4567890), 123),
            AggregatedFact.create(
                BigInteger.valueOf(1)
                    .shiftLeft(128)
                    .subtract(BigInteger.valueOf(1)) /* = 2^128-1 */,
                345));
  }

  /**
   * Creates a job via the frontend API, waits for the job to be processed, and checks the result.
   */
  @Test
  public void createDebugJobE2ETest() throws Exception {
    // Inputs
    String inputBucket = getS3Bucket(INPUT_DATA_URI);
    String inputKey = getS3Key(INPUT_DATA_URI);
    String outputDomainBucket = getS3Bucket(OUPUT_DOMAIN_URI);
    String outputDomainKey = getS3Key(OUPUT_DOMAIN_URI);

    // Outputs
    String outputBucket = getS3Bucket(OUTPUT_DATA_URI);
    String outputKey = getS3Key(OUTPUT_DATA_URI);

    // Create the job and wait for the result
    CreateJobRequest createJobRequest =
        AwsWorkerContinuousTestHelper.createJobRequestWithAttributionReportTo(
            inputBucket,
            inputKey,
            outputBucket,
            outputKey,
            /* debugRun= */ true,
            /* jobId= */ getClass().getSimpleName() + "::" + name.getMethodName(),
            Optional.of(outputDomainBucket),
            Optional.of(outputDomainKey));
    JsonNode result = submitJobAndWaitForResult(createJobRequest, COMPLETION_TIMEOUT);

    assertThat(result.get("result_info").get("return_code").asText()).isEqualTo(SUCCESS.name());

    // Read output avro from s3.
    ImmutableList<AggregatedFact> aggregatedFacts =
        readResultsFromS3(
            s3BlobStorageClient, avroResultsFileReader, outputBucket, getOutputFileName(outputKey));

    // NOTE: this result assertion assumes constant noising, if run with a worker using other
    // noising then this will fail.
    assertThat(aggregatedFacts)
        .containsExactly(
            AggregatedFact.create(BigInteger.valueOf(0), 345),
            AggregatedFact.create(BigInteger.valueOf(1).shiftLeft(120) /* = 2^120 */, 2),
            AggregatedFact.create(BigInteger.valueOf(1234), 505),
            AggregatedFact.create(BigInteger.valueOf(4567890), 123),
            AggregatedFact.create(
                BigInteger.valueOf(1)
                    .shiftLeft(128)
                    .subtract(BigInteger.valueOf(1)) /* = 2^128-1 */,
                345));

    // Read debug output avro from s3.
    ImmutableList<AggregatedFact> aggregatedDebugFacts =
        readDebugResultsFromS3(
            s3BlobStorageClient, readerFactory, outputBucket, getDebugFilePrefix(outputKey));

    // The debug output facts should be the same as unnoisedAggregatedFactsResult
    // because all the reports are "debug enabled".
    assertThat(aggregatedDebugFacts)
        .containsExactly(
            AggregatedFact.create(BigInteger.valueOf(0), 345, 345L),
            AggregatedFact.create(BigInteger.valueOf(1).shiftLeft(120) /* = 2^120 */, 2, 2L),
            AggregatedFact.create(BigInteger.valueOf(1234), 505, 505L),
            AggregatedFact.create(BigInteger.valueOf(4567890), 123, 123L),
            AggregatedFact.create(
                BigInteger.valueOf(1)
                    .shiftLeft(128)
                    .subtract(BigInteger.valueOf(1)) /* = 2^128-1 */,
                345,
                345L));
  }

  private static class TestEnv extends AbstractModule {

    @Override
    protected void configure() {
      bind(S3Client.class)
          .toInstance(
              S3Client.builder()
                  .region(AWS_S3_BUCKET_REGION)
                  .httpClient(UrlConnectionHttpClient.builder().build())
                  .build());
      bind(S3AsyncClient.class)
          .toInstance(S3AsyncClient.builder().region(AWS_S3_BUCKET_REGION).build());
      bind(Boolean.class).annotatedWith(S3UsePartialRequests.class).toInstance(false);
      bind(Integer.class).annotatedWith(PartialRequestBufferSize.class).toInstance(20);
    }
  }
}
