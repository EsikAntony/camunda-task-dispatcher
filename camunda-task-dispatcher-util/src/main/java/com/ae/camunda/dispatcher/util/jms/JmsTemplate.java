package com.ae.camunda.dispatcher.util.jms;

import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.support.JmsUtils;
import org.springframework.util.Assert;

import javax.jms.*;

public class JmsTemplate extends org.springframework.jms.core.JmsTemplate {

    public JmsTemplate(final ConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public void send(final String destinationName, final long messageTtl, final MessageCreator messageCreator) {
        execute(session -> {
            Destination destination = resolveDestinationName(session, destinationName);
            doSend(session, destination, messageTtl, messageCreator);
            return null;
        }, false);
    }

    protected void doSend(final Session session
            , final Destination destination
            , final long messageTtl
            , final MessageCreator messageCreator)
            throws JMSException {

        Assert.notNull(messageCreator, "MessageCreator must not be null");
        MessageProducer producer = createProducer(session, destination, messageTtl);
        try {
            Message message = messageCreator.createMessage(session);
            if (logger.isDebugEnabled()) {
                logger.debug("Sending created message: " + message);
            }
            doSend(producer, message);
            if (session.getTransacted() && isSessionLocallyTransacted(session)) {
                JmsUtils.commitIfNecessary(session);
            }
        }
        finally {
            JmsUtils.closeMessageProducer(producer);
        }
    }

    protected MessageProducer createProducer(final Session session, final Destination destination, final long messageTtl) throws JMSException {
        MessageProducer producer = createProducer(session, destination);
        producer.setTimeToLive(messageTtl);

        return producer;
    }
}
