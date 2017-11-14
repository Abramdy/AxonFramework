/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.commandhandling.model.inspection;

import org.axonframework.commandhandling.model.AggregateMember;
import org.axonframework.commandhandling.model.ForwardingMode;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.property.Property;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;

/**
 * Implementation of a {@link AbstractChildEntityCollectionDefinition} that is used to detect Maps with entities as
 * values annotated with {@link AggregateMember}. If such a field is found a {@link ChildEntity} is created that
 * delegates to the entities in the annotated Map.
 */
public class AggregateMemberAnnotatedChildEntityMapDefinition extends AbstractChildEntityCollectionDefinition {

    @Override
    protected Optional<Class<?>> resolveGenericType(Field field) {
        return ReflectionUtils.resolveGenericType(field, 1);
    }

    @Override
    public <T> Optional<ChildEntity<T>> createChildDefinition(Field field, EntityModel<T> declaringEntity) {
        Map<String, Object> attributes = findAnnotationAttributes(field, AggregateMember.class).orElse(null);
        if (attributes == null || !Map.class.isAssignableFrom(field.getType())) {
            return Optional.empty();
        }
        EntityModel<Object> childEntityModel = declaringEntity.modelOf(resolveType(attributes, field));

        Boolean forwardEvents = (Boolean) attributes.get("forwardEvents");
        ForwardingMode eventForwardingMode = (ForwardingMode) attributes.get("eventForwardingMode");
        Map<String, Property<Object>> routingKeyProperties = extractCommandHandlerRoutingKeys(field, childEntityModel);

        return Optional.of(new AnnotatedChildEntity<>(
                childEntityModel,
                (Boolean) attributes.get("forwardCommands"),
                eventForwardingMode(forwardEvents, eventForwardingMode),
                (String) attributes.get("eventRoutingKey"),
                (msg, parent) -> {
                    Object routingValue = routingKeyProperties.get(msg.getCommandName()).getValue(msg.getPayload());
                    Map<?, ?> fieldValue = ReflectionUtils.getFieldValue(field, parent);
                    return fieldValue == null ? null : fieldValue.get(routingValue);
                },
                (msg, parent) -> {
                    Map<?, Object> fieldValue = ReflectionUtils.getFieldValue(field, parent);
                    return fieldValue == null ? Collections.emptyList() : fieldValue.values();
                }
        ));
    }
}
