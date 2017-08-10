package com.maihaoche.starter.mq.base;

import com.google.gson.Gson;
import com.maihaoche.starter.mq.MQException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.selector.SelectMessageQueueByHash;
import org.apache.rocketmq.common.message.Message;

import javax.annotation.PreDestroy;
import java.nio.charset.Charset;

/**
 * Created by yipin on 2017/6/27.
 * RocketMQ的生产者的抽象基类
 */
@Slf4j
public abstract class AbstractMQProducer {

    private static Gson gson = new Gson();

    private MessageQueueSelector messageQueueSelector = new SelectMessageQueueByHash();

    public AbstractMQProducer() {
    }

    private String tag;

    /**
     * 重写此方法,或者通过setter方法注入tag设置producer bean 级别的tag
     *
     * @return tag
     */
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    @Setter
    private DefaultMQProducer producer;

    @PreDestroy
    public void destroyProducer() {
        if (producer != null) {
            synchronized (AbstractMQProducer.class) {
                if (producer != null) {
                    producer.shutdown();
                }
            }
        }
    }

    private String topic;

    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * 重写此方法定义bean级别的topic，如果有返回有效topic，可以直接使用 sendMessage() 方法发送消息
     *
     * @return
     */
    public String getTopic() {
        return this.topic;
    }

    private Message genMessage(String topic, String tag,String keys, Object msgObj) {
        String str = gson.toJson(msgObj);
        if(StringUtils.isEmpty(topic)) {
            if(StringUtils.isEmpty(getTopic())) {
                throw new RuntimeException("no topic defined to send this message");
            }
            topic = getTopic();
        }
        Message message = new Message(topic, str.getBytes(Charset.forName("utf-8")));
        if (!StringUtils.isEmpty(tag)) {
            message.setTags(tag);
        } else if (!StringUtils.isEmpty(getTag())) {
            message.setTags(getTag());
        }
        //add by ybwei start
        if(!StringUtils.isEmpty(keys)) {
        	message.setKeys(keys);
        }
        //add by ybwei end
        return message;
    }


    /**
     * fire and forget 不关心消息是否送达，可以提高发送tps
     *
     * @param topic
     * @param tag
     * @param keys
     * @param msgObj
     * @throws MQException
     */
    public void sendOneWay(String topic, String tag,String keys,Object msgObj) throws MQException {
        try {
            if(null == msgObj) {
                return;
            }
            producer.sendOneway(genMessage(topic, tag,keys, msgObj));
            log.info("send onway message success : {}", msgObj);
        } catch (Exception e) {
            log.error("消息发送失败，topic : {}, msgObj {}", topic, msgObj);
            throw new MQException("消息发送失败，topic :" + topic + ",e:" + e.getMessage());
        }
    }

    /**
     * fire and forget 不关心消息是否送达，可以提高发送tps
     *
     * @param keys
     * @param msgObj
     * @throws MQException
     */
    public void sendOneWay(String keys,Object msgObj) throws MQException {
        sendOneWay("", "",keys, msgObj);
    }

    /**
     * fire and forget 不关心消息是否送达，可以提高发送tps
     *
     * @param tag
     * @param keys
     * @param msgObj
     * @throws MQException
     */
    public void sendOneWay(String tag, String keys,Object msgObj) throws MQException {
        sendOneWay("", tag, keys, msgObj);
    }


    /**
     * 可以保证同一个queue有序
     *
     * @param topic
     * @param tag
     * @param keys
     * @param msgObj
     * @param hashKey 用于hash后选择queue的key
     */
    public void sendOneWayOrderly(String topic, String tag, String keys, Object msgObj, String hashKey) {
        if(null == msgObj) {
            return;
        }
        if(StringUtils.isEmpty(hashKey)) {
            // fall back to normal
            sendOneWay(topic, tag, msgObj);
        }
        try {
            producer.sendOneway(genMessage(topic, tag, keys, msgObj), messageQueueSelector, hashKey);
            log.info("send onway message orderly success : {}", msgObj);
        } catch (Exception e) {
            log.error("消息发送失败，topic : {}, msgObj {}", topic, msgObj);
            throw new MQException("顺序消息发送失败，topic :" + topic + ",e:" + e.getMessage());
        }
    }

    /**
     * 同步发送消息
     * @param topic  topic
     * @param tag tag
     * @param keys keys
     * @param msgObj  消息体
     * @throws MQException
     */
    public void synSend(String topic, String tag, String keys, Object msgObj) throws MQException {
        try {
            if(null == msgObj) {
                return;
            }
            SendResult sendResult = producer.send(genMessage(topic, tag, keys, msgObj));
            log.info("send rocketmq message ,messageId : {}", sendResult.getMsgId());
            this.doAfterSynSend(sendResult);
        } catch (Exception e) {
            log.error("消息发送失败，topic : {}, msgObj {}", topic, msgObj);
            throw new MQException("消息发送失败，topic :" + topic + ",e:" + e.getMessage());
        }
    }

    /**
     * 同步发送消息
     * @param keys
     * @param msgObj  消息体
     * @throws MQException
     */
    public void synSend(String keys,Object msgObj) throws MQException {
        synSend("", "", keys, msgObj);
    }

    /**
     * 同步发送消息
     * @param tag  消息tag
     * @param keys  消息keys
     * @param msgObj  消息体
     * @throws MQException
     */
    public void synSend(String tag, String keys, Object msgObj) throws MQException {
        synSend("", tag, keys, msgObj);
    }

    /**
     * 同步发送消息
     * @param topic  topic
     * @param tag tag
     * @param keys keys
     * @param msgObj  消息体
     * @param hashKey  用于hash后选择queue的key
     * @throws MQException
     */
    public void synSendOrderly(String topic, String tag, String keys, Object msgObj, String hashKey) throws MQException {
        if(null == msgObj) {
            return;
        }
        if(StringUtils.isEmpty(hashKey)) {
            // fall back to normal
            synSend(topic, tag, msgObj);
        }
        try {
            SendResult sendResult = producer.send(genMessage(topic, tag, keys, msgObj), messageQueueSelector, hashKey);
            log.info("send rocketmq message orderly ,messageId : {}", sendResult.getMsgId());
            this.doAfterSynSend(sendResult);
        } catch (Exception e) {
            log.error("顺序消息发送失败，topic : {}, msgObj {}", topic, msgObj);
            throw new MQException("顺序消息发送失败，topic :" + topic + ",e:" + e.getMessage());
        }
    }

    /**
     * 异步发送消息带tag
     * @param topic topic
     * @param tag tag
     * @param keys keys
     * @param msgObj msgObj
     * @param sendCallback 回调
     * @throws MQException
     */
    public void asynSend(String topic, String tag, String keys, Object msgObj, SendCallback sendCallback) throws MQException {
        try {
            if (null == msgObj) {
                return;
            }
            producer.send(genMessage(topic, tag, keys, msgObj), sendCallback);
            log.info("send rocketmq message asyn");
        } catch (Exception e) {
            log.error("消息发送失败，topic : {}, msgObj {}", topic, msgObj);
            throw new MQException("消息发送失败，topic :" + topic + ",e:" + e.getMessage());
        }
    }

    /**
     * 异步发送消息不带tag和topic
     * @param msgObj keys
     * @param msgObj msgObj
     * @param sendCallback 回调
     * @throws MQException
     */
    public void asynSend(String keys,Object msgObj, SendCallback sendCallback) throws MQException {
        asynSend("", "", keys, msgObj, sendCallback);
    }

    /**
     * 异步发送消息不带tag和topic
     * @param tag msgtag
     * @param keys keys
     * @param msgObj msgObj
     * @param sendCallback 回调
     * @throws MQException
     */
    public void asynSend(String tag, String keys, Object msgObj, SendCallback sendCallback) throws MQException {
        asynSend("", tag, keys, msgObj, sendCallback);
    }

    /**
     * 异步发送消息带tag
     * @param topic topic
     * @param tag tag
     * @param keys keys
     * @param msgObj msgObj
     * @param sendCallback 回调
     * @param hashKey 用于hash后选择queue的key
     * @throws MQException
     */
    public void asynSend(String topic, String tag, String keys, Object msgObj, SendCallback sendCallback, String hashKey) throws MQException {
        if (null == msgObj) {
            return;
        }
        if(StringUtils.isEmpty(hashKey)) {
            // fall back to normal
            asynSend(topic, tag, msgObj, sendCallback);
        }
        try {
            producer.send(genMessage(topic, tag, keys, msgObj), messageQueueSelector, hashKey, sendCallback);
            log.info("send rocketmq message asyn");
        } catch (Exception e) {
            log.error("消息发送失败，topic : {}, msgObj {}", topic, msgObj);
            throw new MQException("消息发送失败，topic :" + topic + ",e:" + e.getMessage());
        }
    }

    /**
     * 兼容buick中的方式
     *
     * @deprecated
     * @param msgObj
     * @throws MQException
     */
    public void sendMessage(Object msgObj) throws MQException {
        if(StringUtils.isEmpty(getTopic())) {
            throw new MQException("如果用这种方式发送消息，请在实例中重写 getTopic() 方法返回需要发送的topic");
        }
        sendOneWay("", "", msgObj);
    }

    /**
     * 重写此方法处理发送后的逻辑
     *
     * @param sendResult  发送结果
     */
    public void doAfterSynSend(SendResult sendResult) {}
}