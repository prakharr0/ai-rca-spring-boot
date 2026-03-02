package io.github.prakharr0.ai.rca.spring.boot.core.context;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class RingBufferLogAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent eventObject) {
        LogBuffer.append(eventObject.getFormattedMessage());
    }
}