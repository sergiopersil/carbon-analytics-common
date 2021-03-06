/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.event.publisher.core.internal.type.json;

import com.google.gson.JsonObject;
import org.wso2.carbon.databridge.commons.Attribute;
import org.wso2.carbon.databridge.commons.StreamDefinition;
import org.wso2.carbon.event.publisher.core.config.EventPublisherConfiguration;
import org.wso2.carbon.event.publisher.core.config.EventPublisherConstants;
import org.wso2.carbon.event.publisher.core.config.mapping.JSONOutputMapping;
import org.wso2.carbon.event.publisher.core.exception.EventPublisherConfigurationException;
import org.wso2.carbon.event.publisher.core.exception.EventPublisherStreamValidationException;
import org.wso2.carbon.event.publisher.core.internal.OutputMapper;
import org.wso2.carbon.event.publisher.core.internal.ds.EventPublisherServiceValueHolder;
import org.wso2.siddhi.core.event.Event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JSONOutputMapper implements OutputMapper {

    private List<String> mappingTextList;
    private EventPublisherConfiguration eventPublisherConfiguration = null;
    private Map<String, Integer> propertyPositionMap = null;
    private final StreamDefinition streamDefinition;

    public JSONOutputMapper(EventPublisherConfiguration eventPublisherConfiguration,
                            Map<String, Integer> propertyPositionMap, int tenantId,
                            StreamDefinition streamDefinition)
            throws EventPublisherConfigurationException {
        this.eventPublisherConfiguration = eventPublisherConfiguration;
        this.propertyPositionMap = propertyPositionMap;
        this.streamDefinition = streamDefinition;
        String mappingText;
        if (eventPublisherConfiguration.getOutputMapping().isCustomMappingEnabled()) {
            mappingText = getCustomMappingText();
            validateStreamDefinitionWithOutputProperties(mappingText);
        } else {
            mappingText = generateJsonEventTemplate(streamDefinition);
        }
        this.mappingTextList = generateMappingTextList(mappingText);
    }


    private List<String> getOutputMappingPropertyList(String mappingText) throws EventPublisherConfigurationException {

        List<String> mappingTextList = new ArrayList<String>();
        String text = mappingText;

        int prefixIndex = text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX);
        int postFixIndex;
        while (prefixIndex > 0) {
            postFixIndex = text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX);
            if (postFixIndex > prefixIndex) {
                mappingTextList.add(text.substring(prefixIndex + 2, postFixIndex));
                text = text.substring(postFixIndex + 2);
            } else {
                throw new EventPublisherConfigurationException("Found template attribute prefix " + EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX
                        + " without corresponding postfix " + EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX + ". Please verify your JSON template.");
            }
            prefixIndex = text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX);
        }
        return mappingTextList;
    }

    private List<String> generateMappingTextList(String mappingText) throws EventPublisherConfigurationException {

        List<String> mappingTextList = new ArrayList<String>();
        String text = mappingText;

        int prefixIndex = text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX);
        int postFixIndex;
        while (prefixIndex > 0) {
            postFixIndex = text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX);
            if (postFixIndex > prefixIndex) {
                mappingTextList.add(text.substring(0, prefixIndex));
                mappingTextList.add(text.substring(prefixIndex + 2, postFixIndex));
                text = text.substring(postFixIndex + 2);
            } else {
                throw new EventPublisherConfigurationException("Found template attribute prefix " + EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX
                        + " without corresponding postfix " + EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX + ". Please verify your JSON template.");
            }
            prefixIndex = text.indexOf(EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX);
        }
        mappingTextList.add(text);
        return mappingTextList;
    }

    @Override
    public Object convertToMappedInputEvent(Event event)
            throws EventPublisherConfigurationException {
        StringBuilder eventText = new StringBuilder(mappingTextList.get(0));
        for (int i = 1, size = mappingTextList.size(); i < size; i++) {
            if (i % 2 == 0) {
                eventText.append(mappingTextList.get(i));
            } else {
                Object propertyValue = getPropertyValue(event.getData(), mappingTextList.get(i));
                if (propertyValue != null && propertyValue instanceof String) {
                    eventText.append(EventPublisherConstants.DOUBLE_QUOTE)
                            .append(propertyValue)
                            .append(EventPublisherConstants.DOUBLE_QUOTE);
                } else {
                    eventText.append(propertyValue);
                }
            }
        }

        return eventText.toString();
    }

    @Override
    public Object convertToTypedInputEvent(Event event)
            throws EventPublisherConfigurationException {
        return convertToMappedInputEvent(event);
    }

    private void validateStreamDefinitionWithOutputProperties(String actualMappingText)
            throws EventPublisherConfigurationException {
        List<String> mappingProperties = getOutputMappingPropertyList(actualMappingText);
        Iterator<String> mappingTextListIterator = mappingProperties.iterator();
        for (; mappingTextListIterator.hasNext(); ) {
            String property = mappingTextListIterator.next();
            if (!propertyPositionMap.containsKey(property)) {
                throw new EventPublisherStreamValidationException("Property " + property + " is not in the input stream definition.", streamDefinition.getStreamId());
            }
        }
    }

    private String getCustomMappingText() throws EventPublisherConfigurationException {
        JSONOutputMapping jsonOutputMapping = ((JSONOutputMapping) eventPublisherConfiguration.getOutputMapping());
        String actualMappingText = jsonOutputMapping.getMappingText();
        if (actualMappingText == null) {
            throw new EventPublisherConfigurationException("Json mapping text is empty!");
        }
        if (jsonOutputMapping.isRegistryResource()) {
            actualMappingText = EventPublisherServiceValueHolder.getCarbonEventPublisherService().getRegistryResourceContent(jsonOutputMapping.getMappingText());
        }
        return actualMappingText;
    }

    private Object getPropertyValue(Object[] eventData, String mappingProperty) {
        if (eventData.length != 0) {
            int position = propertyPositionMap.get(mappingProperty);
            return eventData[position];
        }
        return null;
    }

    private String generateJsonEventTemplate(StreamDefinition streamDefinition) {

        JsonObject jsonEventObject = new JsonObject();
        JsonObject innerParentObject = new JsonObject();

        List<Attribute> metaDatAttributes = streamDefinition.getMetaData();
        if (metaDatAttributes != null && metaDatAttributes.size() > 0) {
            innerParentObject.add(EventPublisherConstants.EVENT_META_TAG, createPropertyElement(EventPublisherConstants.PROPERTY_META_PREFIX, metaDatAttributes));
        }

        List<Attribute> correlationAttributes = streamDefinition.getCorrelationData();
        if (correlationAttributes != null && correlationAttributes.size() > 0) {
            innerParentObject.add(EventPublisherConstants.EVENT_CORRELATION_TAG, createPropertyElement(EventPublisherConstants.PROPERTY_CORRELATION_PREFIX, correlationAttributes));
        }

        List<Attribute> payloadAttributes = streamDefinition.getPayloadData();
        if (payloadAttributes != null && payloadAttributes.size() > 0) {
            innerParentObject.add(EventPublisherConstants.EVENT_PAYLOAD_TAG, createPropertyElement("", payloadAttributes));
        }

        jsonEventObject.add(EventPublisherConstants.EVENT_PARENT_TAG, innerParentObject);

        String defaultMapping = jsonEventObject.toString();
        defaultMapping = defaultMapping.replaceAll("\"\\{\\{", "{{");
        defaultMapping = defaultMapping.replaceAll("\\}\\}\"", "}}");

        return defaultMapping;
    }

    private static JsonObject createPropertyElement(String dataPrefix,
                                                    List<Attribute> attributeList) {

        JsonObject innerObject = new JsonObject();
        for (Attribute attribute : attributeList) {
            innerObject.addProperty(attribute.getName(), EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_PREFIX + dataPrefix + attribute.getName() + EventPublisherConstants.TEMPLATE_EVENT_ATTRIBUTE_POSTFIX);
        }
        return innerObject;
    }


}
