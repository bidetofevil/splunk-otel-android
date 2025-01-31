/*
 * Copyright Splunk Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.rum.internal;

import android.app.Application;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.rum.internal.instrumentation.InstrumentedApplication;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class SdkPreconfiguredRumBuilder {
    private final Application application;
    private final OpenTelemetrySdk sdk;
    private final SessionId sessionId;

    private final List<Consumer<InstrumentedApplication>> instrumentationInstallers =
            new ArrayList<>();

    SdkPreconfiguredRumBuilder(Application application, OpenTelemetrySdk openTelemetrySdk) {
        this(application, openTelemetrySdk, new SessionId(new SessionIdTimeoutHandler()));
    }

    SdkPreconfiguredRumBuilder(
            Application application, OpenTelemetrySdk openTelemetrySdk, SessionId sessionId) {
        this.application = application;
        this.sdk = openTelemetrySdk;
        this.sessionId = sessionId;
    }

    /**
     * Adds an instrumentation installer function that will be run on an {@link
     * InstrumentedApplication} instance as a part of the {@link #build()} method call.
     *
     * @return {@code this}
     */
    public SdkPreconfiguredRumBuilder addInstrumentation(
            Consumer<InstrumentedApplication> instrumentationInstaller) {
        instrumentationInstallers.add(instrumentationInstaller);
        return this;
    }

    /**
     * Creates a new instance of {@link OpenTelemetryRum} with the settings of this {@link
     * OpenTelemetryRumBuilder}.
     *
     * <p>This method uses a preconfigured OpenTelemetry SDK and install built-in system
     * instrumentations in the passed Android {@link Application}.
     *
     * @return A new {@link OpenTelemetryRum} instance.
     */
    public OpenTelemetryRum build() {
        // the app state listeners need to be run in the first ActivityLifecycleCallbacks since they
        // might turn off/on additional telemetry depending on whether the app is active or not
        ApplicationStateWatcher applicationStateWatcher = new ApplicationStateWatcher();
        application.registerActivityLifecycleCallbacks(applicationStateWatcher);
        applicationStateWatcher.registerListener(sessionId.getTimeoutHandler());

        Tracer tracer = sdk.getTracer(OpenTelemetryRum.class.getSimpleName());
        sessionId.setSessionIdChangeListener(new SessionIdChangeTracer(tracer));

        InstrumentedApplication instrumentedApplication =
                new InstrumentedApplicationImpl(application, sdk, applicationStateWatcher);
        for (Consumer<InstrumentedApplication> installer : instrumentationInstallers) {
            installer.accept(instrumentedApplication);
        }

        return new OpenTelemetryRumImpl(sdk, sessionId);
    }
}
