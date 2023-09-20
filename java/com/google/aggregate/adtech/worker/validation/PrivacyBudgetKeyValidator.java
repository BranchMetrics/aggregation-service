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

import com.google.aggregate.adtech.worker.model.ErrorMessage;
import com.google.aggregate.adtech.worker.model.SharedInfo;
import java.util.Optional;

/**
 * PrivacyBudgetKeyValidator is used to validate SharedInfo fields used for generating privacy
 * budget key for Reports.
 */
public interface PrivacyBudgetKeyValidator {
  String NULL_OR_INVALID_SHAREDINFO_FIELD_ERROR_STRING =
      "One or more required fields in report's SharedInfo are null or invalid.";

  Optional<ErrorMessage> validatePrivacyBudgetKey(SharedInfo sharedInfo);
}
