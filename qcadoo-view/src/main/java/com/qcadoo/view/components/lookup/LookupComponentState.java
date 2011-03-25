/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 0.3.0
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */

package com.qcadoo.view.components.lookup;

import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.util.StringUtils;

import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.FieldDefinition;
import com.qcadoo.model.api.search.Restrictions;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchResult;
import com.qcadoo.model.api.utils.ExpressionUtils;
import com.qcadoo.view.components.FieldComponentState;
import com.qcadoo.view.internal.components.lookup.LookupComponentPattern;

public final class LookupComponentState extends FieldComponentState {

    public static final String JSON_REQUIRED = "required";

    public static final String JSON_TEXT = "selectedEntityValue";

    public static final String JSON_CODE = "selectedEntityCode";

    public static final String JSON_CURRENT_CODE = "currentCode";

    public static final String JSON_CLEAR_CURRENT_CODE = "clearCurrentCodeCode";

    public static final String JSON_BELONGS_TO_ENTITY_ID = "contextEntityId";

    public static final String JSON_AUTOCOMPLETE_MATCHES = "autocompleteMatches";

    public static final String JSON_AUTOCOMPLETE_CODE = "autocompleteCode";

    public static final String JSON_AUTOCOMPLETE_ENTITIES_NUMBER = "autocompleteEntitiesNumber";

    private final LookupEventPerformer eventPerformer = new LookupEventPerformer();

    private final FieldDefinition belongsToFieldDefinition;

    private Long belongsToEntityId;

    private String currentCode;

    private boolean clearCurrentCodeCode = false;

    private String selectedEntityCode;

    private String selectedEntityValue;

    private final String fieldCode;

    private final String expression;

    private String autocompleteCode;

    private List<Entity> autocompleteMatches;

    private int autocompleteEntitiesNumber;

    public LookupComponentState(final FieldDefinition scopeField, final String fieldCode, final String expression,
            final LookupComponentPattern pattern) {
        super(pattern);
        this.belongsToFieldDefinition = scopeField;
        this.fieldCode = fieldCode;
        this.expression = expression;
        registerEvent("initialize", eventPerformer, "initialize");
        registerEvent("autompleteSearch", eventPerformer, "autompleteSearch");
        registerEvent("onSelectedEntityChange", eventPerformer, "onSelectedEntityChange");
    }

    @Override
    protected void initializeContent(final JSONObject json) throws JSONException {
        super.initializeContent(json);

        if (json.has(JSON_TEXT) && !json.isNull(JSON_TEXT)) {
            selectedEntityValue = json.getString(JSON_TEXT);
        }
        if (json.has(JSON_CODE) && !json.isNull(JSON_CODE)) {
            selectedEntityCode = json.getString(JSON_CODE);
        }
        if (json.has(JSON_BELONGS_TO_ENTITY_ID) && !json.isNull(JSON_BELONGS_TO_ENTITY_ID)) {
            belongsToEntityId = json.getLong(JSON_BELONGS_TO_ENTITY_ID);
        }

        if (json.has(JSON_CURRENT_CODE) && !json.isNull(JSON_CURRENT_CODE)) {
            currentCode = json.getString(JSON_CURRENT_CODE);
        }

        if (json.has(JSON_AUTOCOMPLETE_CODE) && !json.isNull(JSON_AUTOCOMPLETE_CODE)) {
            autocompleteCode = json.getString(JSON_AUTOCOMPLETE_CODE);
        }

        if (belongsToFieldDefinition != null && belongsToEntityId == null) {
            setEnabled(false);
        }
    }

    @Override
    protected JSONObject renderContent() throws JSONException {
        JSONObject json = super.renderContent();
        json.put(JSON_TEXT, selectedEntityValue);
        json.put(JSON_CODE, selectedEntityCode);
        json.put(JSON_BELONGS_TO_ENTITY_ID, belongsToEntityId);

        if (clearCurrentCodeCode) {
            json.put(JSON_CLEAR_CURRENT_CODE, clearCurrentCodeCode);
        }

        if (autocompleteMatches != null) {
            JSONArray matches = new JSONArray();
            for (Entity entity : autocompleteMatches) {
                JSONObject matchEntity = new JSONObject();
                matchEntity.put("id", entity.getId());
                matchEntity.put("value", ExpressionUtils.getValue(entity, expression, getLocale()));
                matchEntity.put("code", String.valueOf(entity.getField(fieldCode)));
                matches.put(matchEntity);
            }
            json.put(JSON_AUTOCOMPLETE_MATCHES, matches);
            json.put(JSON_AUTOCOMPLETE_CODE, autocompleteCode);
            json.put(JSON_AUTOCOMPLETE_ENTITIES_NUMBER, autocompleteEntitiesNumber);

        }

        return json;
    }

    @Override
    public Long getFieldValue() {
        Long entityId = getFieldValueWithoutSearching();

        // if (entityId == null && StringUtils.hasText(code)) {
        // eventPerformer.search(new String[0]);
        // return getFieldValueWithoutSearching();
        // } else {
        return entityId;
        // }
    }

    public Long getFieldValueWithoutSearching() {
        return convertToLong(super.getFieldValue());
    }

    @Override
    public void setFieldValue(final Object value) {
        setFieldValueWithoutRefreshing(convertToLong(value));
        if (!this.isHasError()) {
            clearCurrentCodeCode = true;
        }
        eventPerformer.refresh();
    }

    private void setFieldValueWithoutRefreshing(final Long value) {
        super.setFieldValue(value);
        notifyEntityIdChangeListeners(convertToLong(value));
    }

    private Long convertToLong(final Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Long) {
            return (Long) value;
        } else if (StringUtils.hasText(value.toString()) && !"null".equals(value.toString())) {
            return Long.parseLong(value.toString());
        } else {
            return null;
        }
    }

    @Override
    public void onScopeEntityIdChange(final Long scopeEntityId) {
        if (belongsToFieldDefinition != null) {
            belongsToEntityId = scopeEntityId;
            setEnabled(scopeEntityId != null);
            requestRender();
        } else {
            throw new IllegalStateException("Lookup doesn't have scopeField, it cannot set scopeEntityId");
        }
    }

    protected class LookupEventPerformer {

        public void initialize(final String[] args) {
            refresh();
            requestRender();
        }

        public void autompleteSearch(final String[] args) {
            if ((belongsToFieldDefinition == null || belongsToEntityId != null)) {
                SearchCriteriaBuilder searchCriteriaBuilder = getDataDefinition().find();

                if (StringUtils.hasText(currentCode)) {
                    searchCriteriaBuilder.restrictedWith(Restrictions.eq(getDataDefinition().getField(fieldCode), currentCode
                            + "*"));
                }

                if (belongsToFieldDefinition != null && belongsToEntityId != null) {
                    searchCriteriaBuilder.restrictedWith(Restrictions.belongsTo(belongsToFieldDefinition, belongsToEntityId));
                }

                searchCriteriaBuilder.orderAscBy(fieldCode);

                SearchResult results = searchCriteriaBuilder.list();

                autocompleteEntitiesNumber = results.getTotalNumberOfEntities();

                if (results.getTotalNumberOfEntities() > 25) {
                    autocompleteMatches = new LinkedList<Entity>();
                } else {
                    autocompleteMatches = results.getEntities();
                }
            } else {
                autocompleteMatches = new LinkedList<Entity>();
            }

            autocompleteCode = currentCode;
            requestRender();
        }

        public void onSelectedEntityChange(final String[] args) {
            notifyEntityIdChangeListeners(getFieldValue());
        }

        private void refresh() {
            Long entityId = getFieldValueWithoutSearching();

            if (entityId != null) {

                Entity entity = getDataDefinition().get(entityId);

                if (entity != null) {
                    selectedEntityCode = String.valueOf(entity.getField(fieldCode));
                    selectedEntityValue = ExpressionUtils.getValue(entity, expression, getLocale());
                } else {
                    setFieldValueWithoutRefreshing(null);
                    selectedEntityCode = "";
                    selectedEntityValue = "";
                }

            } else {
                selectedEntityCode = "";
                selectedEntityValue = "";
            }
        }

    }

}