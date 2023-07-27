/*
 * Copyright 2023 Google LLC
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

package com.google.privacysandbox.otel;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OTelConfigurationImplTest {

  private static final Clock CLOCK = Clock.getDefault();
  private static final AttributeKey<String> JOB_ID_KEY = AttributeKey.stringKey("job-id");
  private final Resource RESOURCE = Resource.getDefault();
  private InMemorySpanExporter spanExporter;
  private InMemoryMetricReader metricReader;
  private OTelConfiguration oTelConfigurationImpl;

  @Before
  public void setUp() {

    // Setup trace provider
    spanExporter = InMemorySpanExporter.create();
    SdkTracerProvider sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .setResource(RESOURCE)
            .setClock(CLOCK)
            .build();

    // Setup meter provider
    metricReader = InMemoryMetricReader.create();
    SdkMeterProvider sdkMeterProvider =
        SdkMeterProvider.builder()
            .setResource(RESOURCE)
            .setClock(CLOCK)
            .registerMetricReader(metricReader)
            .build();

    // Setup OpenTelemetry object
    OpenTelemetry openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .setMeterProvider(sdkMeterProvider)
            .build();

    oTelConfigurationImpl = new OTelConfigurationImpl(openTelemetry);
  }

  private void assertCounterValue(String name, long val) {
    // There is only one MetricData in The list.
    MetricData metric = metricReader.collectAllMetrics().stream().collect(toImmutableList()).get(0);
    // This needs to be cast to LongPointData from PointData because there is no getValue method in
    // PointData.
    LongPointData point =
        (LongPointData) metric.getData().getPoints().stream().collect(toImmutableList()).get(0);

    assertThat(metric.getName()).isEqualTo(name);
    assertThat(point.getValue()).isEqualTo(val);
  }

  @Test
  public void createDebugCounter_ensuresIncrement() {
    String counterName = "counter";
    long counterValue1 = 3;
    long counterValue2 = 4;
    LongCounter counter = oTelConfigurationImpl.createDebugCounter(counterName);

    counter.add(counterValue1);
    assertCounterValue(counterName, counterValue1);

    counter.add(counterValue2);
    assertCounterValue(counterName, counterValue1 + counterValue2);
  }

  @Test
  public void createDebugCounter_ensuresNoDecrement() {
    String counterName = "counter";
    long counterValue = 3;
    LongCounter counter = oTelConfigurationImpl.createDebugCounter(counterName);

    counter.add(counterValue);
    assertCounterValue(counterName, counterValue);

    // negative values do not decrement counters
    counter.add(-2);
    assertCounterValue(counterName, counterValue);
  }

  @Test
  public void createProdCounter_ensuresIncrement() {
    String counterName = "counter";
    long counterValue1 = 3;
    long counterValue2 = 4;
    LongCounter counter = oTelConfigurationImpl.createProdCounter(counterName);

    counter.add(counterValue1);
    assertCounterValue(counterName, counterValue1);

    counter.add(counterValue2);
    assertCounterValue(counterName, counterValue1 + counterValue2);
  }

  @Test
  public void createProdCounter_ensuresNoDecrement() {
    String counterName = "counter";
    long counterValue = 3;
    LongCounter counter = oTelConfigurationImpl.createProdCounter(counterName);

    counter.add(counterValue);
    assertCounterValue(counterName, counterValue);

    // negative values do not decrement counters
    counter.add(-2);
    assertCounterValue(counterName, counterValue);
  }

  private void assertGaugeNonNull(String name, String unit) {
    MetricData metric = metricReader.collectAllMetrics().stream().collect(toImmutableList()).get(0);
    DoublePointData point =
        (DoublePointData) metric.getData().getPoints().stream().collect(toImmutableList()).get(0);

    assertThat(metric.getName()).isEqualTo(name);
    assertThat(metric.getUnit()).isEqualTo(unit);
    assertThat(point.getValue()).isNotNull();
  }

  @Test
  public void createProdMemoryUtilizationGauge_isNotNull() {
    String gaugeName = "process.runtime.jvm.memory.utilization";

    oTelConfigurationImpl.createProdMemoryUtilizationGauge();

    assertGaugeNonNull(gaugeName, "MiB");
  }

  @Test
  public void createProdMemoryUtilizationRatioGauge_isNotNull() {
    String gaugeName = "process.runtime.jvm.memory.utilization_ratio";

    oTelConfigurationImpl.createProdMemoryUtilizationRatioGauge();

    assertGaugeNonNull(gaugeName, "percent");
  }

  private void assertTimerNames(ImmutableList<String> timerNames) {
    List<SpanData> spanItems = spanExporter.getFinishedSpanItems();
    assertThat(spanItems).isNotNull();
    ImmutableList<String> spanNames = spanItems.stream().map(SpanData::getName).collect(toImmutableList());
    assertThat(spanNames).containsExactlyElementsIn(timerNames).inOrder();
  }

  @Test
  public void createDebugTimerStarted_createsTimersInOrder() {
    ImmutableList<String> timerNames = ImmutableList.of("debugTimer1", "debugTimer2");

    try (Timer ignore = oTelConfigurationImpl.createDebugTimerStarted(timerNames.get(0))) {}
    try (Timer ignore = oTelConfigurationImpl.createDebugTimerStarted(timerNames.get(1))) {}

    assertTimerNames(timerNames);
  }

  @Test
  public void createProdTimerStarted_createsTimersInOrder() {
    ImmutableList<String> timerNames = ImmutableList.of("prodTimer1", "prodTimer2");

    try (Timer ignore = oTelConfigurationImpl.createProdTimerStarted(timerNames.get(0))) {}
    try (Timer ignore = oTelConfigurationImpl.createProdTimerStarted(timerNames.get(1))) {}

    assertTimerNames(timerNames);
  }

  @Test
  public void createDebugTimerStarted_setsJobIDWhenProvided() {
    String timerName = "debugTimer";
    String jobID = "1234";

    try (Timer ignore = oTelConfigurationImpl.createDebugTimerStarted(timerName, jobID)) {}
    SpanData spanData = spanExporter.getFinishedSpanItems().get(0);

    assertThat(spanData.getName()).isEqualTo(timerName);
    assertThat(spanData.getAttributes().get(JOB_ID_KEY)).isEqualTo(jobID);
  }

  @Test
  public void createProdTimerStarted_setsJobIDWhenProvided() {
    String timerName = "prodTimer";
    String jobID = "2234";

    try (Timer ignore = oTelConfigurationImpl.createProdTimerStarted(timerName, jobID)) {}
    SpanData spanData = spanExporter.getFinishedSpanItems().get(0);

    assertThat(spanData.getName()).isEqualTo(timerName);
    assertThat(spanData.getAttributes().get(JOB_ID_KEY)).isEqualTo(jobID);
  }

  @Test
  public void createDebugTimerStarted_orderMaintainedInNested() {
    try (Timer ignore = oTelConfigurationImpl.createDebugTimerStarted("debugTimer1")) {
      try (Timer ignored = oTelConfigurationImpl.createDebugTimerStarted("debugTimer2")) {}
    }
    try (Timer ignored = oTelConfigurationImpl.createDebugTimerStarted("debugTimer3")) {}

    assertTimerNames(ImmutableList.of("debugTimer2", "debugTimer1", "debugTimer3"));
  }

  @Test
  public void createProdTimerStarted_orderMaintainedInNested() {
    try (Timer ignore = oTelConfigurationImpl.createProdTimerStarted("prodTimer1")) {
      try (Timer ignored = oTelConfigurationImpl.createProdTimerStarted("prodTimer2")) {}
    }
    try (Timer ignored = oTelConfigurationImpl.createProdTimerStarted("prodTimer3")) {}

    assertTimerNames(ImmutableList.of("prodTimer2", "prodTimer1", "prodTimer3"));
  }

  @Test
  public void createDebugTimerStarted_addEventSucceeds() {
    String timerName = "debugTimer";
    String eventName = "debugEvent";

    try (Timer timer = oTelConfigurationImpl.createDebugTimerStarted(timerName)) {
      timer.addEvent(eventName);
    }
    SpanData spanData = spanExporter.getFinishedSpanItems().get(0);

    assertThat(spanData.getName()).isEqualTo(timerName);
    assertThat(spanData.getEvents().get(0).getName()).isEqualTo(eventName);
  }

  @Test
  public void createProdTimerStarted_addEventSucceeds() {
    String timerName = "prodTimer";
    String eventName = "prodEvent";

    try (Timer timer = oTelConfigurationImpl.createProdTimerStarted(timerName)) {
      timer.addEvent(eventName);
    }
    SpanData spanData = spanExporter.getFinishedSpanItems().get(0);

    assertThat(spanData.getName()).isEqualTo(timerName);
    assertThat(spanData.getEvents().get(0).getName()).isEqualTo(eventName);
  }
}
