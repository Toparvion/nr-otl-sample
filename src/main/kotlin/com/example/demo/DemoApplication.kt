package com.example.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.Aggregation
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector
import io.opentelemetry.sdk.metrics.export.DefaultAggregationSelector
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@SpringBootApplication
class DemoApplication {
  @Bean
  fun openTelemetry(): OpenTelemetry {
    return OpenTelemetrySdk.builder()
      .setMeterProvider(
        SdkMeterProvider.builder()
          .setResource(
            Resource.getDefault().toBuilder()
              .put("service.name", "micrometer-shim")
              // Include instrumentation.provider=micrometer to enable micrometer metrics experience in New Relic
              .put("instrumentation.provider", "micrometer")
              .build()
          )
          .registerMetricReader(
            PeriodicMetricReader.builder(
              OtlpGrpcMetricExporter.builder()
                .setEndpoint("https://otlp.nr-data.net:4317")
                .addHeader("api-key", System.getenv("NEW_RELIC_LICENSE_KEY")) 
                // IMPORTANT: New Relic requires metrics to be delta temporality
                .setAggregationTemporalitySelector(
                  AggregationTemporalitySelector.deltaPreferred()
                ) // Use exponential histogram aggregation for histogram instruments
                // to produce better data and compression
                .setDefaultAggregationSelector(
                  DefaultAggregationSelector.getDefault()
                    .with(
                      InstrumentType.HISTOGRAM,
                      Aggregation.base2ExponentialBucketHistogram()
                    )
                )
                .build()) // Match default micrometer collection interval of 60 seconds
              .setInterval(Duration.ofSeconds(60))
              .build())
          .build())
      .build()
  }

  @Bean
  fun meterRegistry(openTelemetry: OpenTelemetry?): MeterRegistry {
    return OpenTelemetryMeterRegistry.builder(openTelemetry).build()
  }
}

fun main(args: Array<String>) {
	runApplication<DemoApplication>(*args)
}
