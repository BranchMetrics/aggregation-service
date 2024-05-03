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

package com.google.scp.operator.frontend.service.aws.changehandler;

import static com.google.scp.operator.protos.shared.backend.JobStatusProto.JobStatus.RECEIVED;

import com.google.inject.Inject;
import com.google.scp.operator.protos.shared.backend.metadatadb.JobMetadataProto.JobMetadata;
import com.google.scp.operator.shared.dao.jobqueue.common.JobQueue;
import com.google.scp.operator.shared.dao.jobqueue.common.JobQueue.JobQueueException;

/** Places recently inserted items on to the JobQueue. */
public final class JobQueueWriteHandler implements JobMetadataChangeHandler {

  private final JobQueue jobQueue;

  /** Creates a new instance of the {@code JobQueueWriteHandler} class. */
  @Inject
  JobQueueWriteHandler(JobQueue jobQueue) {
    this.jobQueue = jobQueue;
  }

  @Override
  public boolean canHandle(JobMetadata jobMetadata) {
    return jobMetadata.getJobStatus().equals(RECEIVED);
  }

  @Override
  public void handle(JobMetadata jobMetadata) {
    try {
      jobQueue.sendJob(jobMetadata.getJobKey(), jobMetadata.getServerJobId());
    } catch (JobQueueException e) {
      throw new ChangeHandlerException(e);
    }
  }
}
