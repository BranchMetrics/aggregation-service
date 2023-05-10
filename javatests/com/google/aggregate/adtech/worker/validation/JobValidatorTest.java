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

package com.google.aggregate.adtech.worker.validation;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.scp.operator.cpio.jobclient.model.Job;
import com.google.scp.operator.cpio.jobclient.testing.FakeJobGenerator;
import com.google.scp.operator.protos.shared.backend.RequestInfoProto.RequestInfo;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class JobValidatorTest {

  private Job.Builder jobBuilder;
  private RequestInfo.Builder requestInfoBuilder;

  @Before
  public void setUp() {
    jobBuilder = FakeJobGenerator.generateBuilder("");
    requestInfoBuilder =
        RequestInfo.newBuilder()
            .setJobRequestId("123")
            .setInputDataBlobPrefix("foo")
            .setInputDataBucketName("foo")
            .setOutputDataBlobPrefix("foo")
            .setOutputDataBucketName("foo");
  }

  @Test
  public void validate_noAttributionReportToKeyInParams_fails() {
    ImmutableMap jobParams = ImmutableMap.of();
    Job job =
        jobBuilder
            .setRequestInfo(requestInfoBuilder.putAllJobParameters(jobParams).build())
            .build();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JobValidator.validate(Optional.of(job), /* domainOptional= */ false));

    assertThat(exception)
        .hasMessageThat()
        .containsMatch("Job parameters does not have an attribution_report_to field for the Job");
  }

  @Test
  public void validate_noAttributionReportTo_fails() {
    ImmutableMap jobParams = ImmutableMap.of("attribution_report_to", "");
    Job job =
        jobBuilder
            .setRequestInfo(requestInfoBuilder.putAllJobParameters(jobParams).build())
            .build();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JobValidator.validate(Optional.of(job), /* domainOptional= */ false));

    assertThat(exception)
        .hasMessageThat()
        .containsMatch("Job parameters does not have an attribution_report_to field for the Job");
  }

  @Test
  public void validate_noOutputDomain_domainOptional_succeeds() {
    ImmutableMap jobParams = ImmutableMap.of("attribution_report_to", "foo.com");
    Job job =
        jobBuilder
            .setRequestInfo(requestInfoBuilder.putAllJobParameters(jobParams).build())
            .build();

    JobValidator.validate(Optional.of(job), /* domainOptional= */ true);
  }

  @Test
  public void validate_noOutputDomain_domainNotOptional_fails() {
    ImmutableMap jobParams = ImmutableMap.of("attribution_report_to", "foo.com");
    Job job =
        jobBuilder
            .setRequestInfo(requestInfoBuilder.putAllJobParameters(jobParams).build())
            .build();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JobValidator.validate(Optional.of(job), /* domainOptional= */ false));
    assertThat(exception)
        .hasMessageThat()
        .containsMatch(
            "Job parameters for the job '' does not have output domain location specified in"
                + " 'output_domain_bucket_name' and 'output_domain_blob_prefix' fields. Please"
                + " refer to the API documentation for output domain parameters at"
                + " https://github.com/privacysandbox/aggregation-service/blob/main/docs/API.md");
  }

  @Test
  public void validate_outputDomainPresent_domainNotOptional_succeeds() {
    ImmutableMap jobParams =
        ImmutableMap.of(
            "attribution_report_to",
            "foo.com",
            "output_domain_blob_prefix",
            "prefix_",
            "output_domain_bucket_name",
            "bucket");
    Job job =
        jobBuilder
            .setRequestInfo(requestInfoBuilder.putAllJobParameters(jobParams).build())
            .build();

    JobValidator.validate(Optional.of(job), /* domainOptional= */ false);
  }

  @Test
  public void validate_noJob_fails() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> JobValidator.validate(Optional.empty(), /* domainOptional= */ false));

    assertThat(exception).hasMessageThat().isEqualTo("Job metadata not found.");
  }
}
