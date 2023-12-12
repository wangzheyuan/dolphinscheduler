/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.api.service.impl;

import static org.apache.dolphinscheduler.common.constants.Constants.CHANGE;
import static org.apache.dolphinscheduler.common.constants.Constants.SMALL;

import org.apache.dolphinscheduler.api.dto.RuleDefinition;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.DqRuleService;
import org.apache.dolphinscheduler.api.utils.PageInfo;
import org.apache.dolphinscheduler.common.utils.DateUtils;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.DataSource;
import org.apache.dolphinscheduler.dao.entity.DqComparisonType;
import org.apache.dolphinscheduler.dao.entity.DqRule;
import org.apache.dolphinscheduler.dao.entity.DqRuleExecuteSql;
import org.apache.dolphinscheduler.dao.entity.DqRuleInputEntry;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.dao.mapper.DataSourceMapper;
import org.apache.dolphinscheduler.dao.mapper.DqComparisonTypeMapper;
import org.apache.dolphinscheduler.dao.mapper.DqRuleExecuteSqlMapper;
import org.apache.dolphinscheduler.dao.mapper.DqRuleInputEntryMapper;
import org.apache.dolphinscheduler.dao.mapper.DqRuleMapper;
import org.apache.dolphinscheduler.dao.utils.DqRuleUtils;
import org.apache.dolphinscheduler.plugin.task.api.enums.dp.OptionSourceType;
import org.apache.dolphinscheduler.spi.enums.DbType;
import org.apache.dolphinscheduler.spi.params.base.FormType;
import org.apache.dolphinscheduler.spi.params.base.ParamsOptions;
import org.apache.dolphinscheduler.spi.params.base.PluginParams;
import org.apache.dolphinscheduler.spi.params.base.PropsType;
import org.apache.dolphinscheduler.spi.params.base.Validate;
import org.apache.dolphinscheduler.spi.params.group.GroupParam;
import org.apache.dolphinscheduler.spi.params.group.GroupParamsProps;
import org.apache.dolphinscheduler.spi.params.input.InputParam;
import org.apache.dolphinscheduler.spi.params.input.InputParamProps;
import org.apache.dolphinscheduler.spi.params.select.SelectParam;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DqRuleServiceImpl
 */
@Service
@Slf4j
public class DqRuleServiceImpl extends BaseServiceImpl implements DqRuleService {

    @Autowired
    private DqRuleMapper dqRuleMapper;

    @Autowired
    private DqRuleInputEntryMapper dqRuleInputEntryMapper;

    @Autowired
    private DqRuleExecuteSqlMapper dqRuleExecuteSqlMapper;

    @Autowired
    private DataSourceMapper dataSourceMapper;

    @Autowired
    private DqComparisonTypeMapper dqComparisonTypeMapper;

    @Override
    public String getRuleFormCreateJsonById(int id) {

        List<DqRuleInputEntry> ruleInputEntryList = dqRuleInputEntryMapper.getRuleInputEntryList(id);
        if (ruleInputEntryList == null || ruleInputEntryList.isEmpty()) {
            throw new ServiceException(Status.QUERY_RULE_INPUT_ENTRY_LIST_ERROR);
        }
        return getRuleFormCreateJson(DqRuleUtils.transformInputEntry(ruleInputEntryList));
    }

    @Override
    public List<DqRule> queryAllRuleList() {
        return dqRuleMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public List<ParamsOptions> getDatasourceOptionsById(int datasourceId) {

        List<DataSource> dataSourceList = dataSourceMapper.listAllDataSourceByType(datasourceId);
        if (CollectionUtils.isEmpty(dataSourceList)) {
            return Collections.emptyList();
        }

        List<ParamsOptions> options = new ArrayList<>();
        for (DataSource dataSource : dataSourceList) {
            ParamsOptions childrenOption = new ParamsOptions(dataSource.getName(), dataSource.getId(), false);
            options.add(childrenOption);
        }
        return options;
    }

    @Override
    public PageInfo<DqRule> queryRuleListPaging(User loginUser,
                                                String searchVal,
                                                Integer ruleType,
                                                String startTime,
                                                String endTime,
                                                Integer pageNo,
                                                Integer pageSize) {

        Date start = null;
        Date end = null;
        try {
            if (StringUtils.isNotEmpty(startTime)) {
                start = DateUtils.stringToDate(startTime);
            }
            if (StringUtils.isNotEmpty(endTime)) {
                end = DateUtils.stringToDate(endTime);
            }
        } catch (Exception e) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "startTime,endTime");
        }

        Page<DqRule> page = new Page<>(pageNo, pageSize);
        PageInfo<DqRule> pageInfo = new PageInfo<>(pageNo, pageSize);

        if (ruleType == null) {
            ruleType = -1;
        }

        IPage<DqRule> dqRulePage =
                dqRuleMapper.queryRuleListPaging(
                        page,
                        searchVal,
                        ruleType,
                        start,
                        end);
        if (dqRulePage != null) {
            List<DqRule> dataList = dqRulePage.getRecords();
            dataList.forEach(dqRule -> {
                List<DqRuleInputEntry> ruleInputEntryList =
                        DqRuleUtils.transformInputEntry(dqRuleInputEntryMapper.getRuleInputEntryList(dqRule.getId()));
                List<DqRuleExecuteSql> ruleExecuteSqlList = dqRuleExecuteSqlMapper.getExecuteSqlList(dqRule.getId());

                RuleDefinition ruleDefinition = new RuleDefinition(ruleInputEntryList, ruleExecuteSqlList);
                dqRule.setRuleJson(JSONUtils.toJsonString(ruleDefinition));
            });

            pageInfo.setTotal((int) dqRulePage.getTotal());
            pageInfo.setTotalList(dataList);
        }

        return pageInfo;
    }

    private String getRuleFormCreateJson(List<DqRuleInputEntry> ruleInputEntryList) {
        List<PluginParams> params = new ArrayList<>();

        for (DqRuleInputEntry inputEntry : ruleInputEntryList) {
            if (Boolean.TRUE.equals(inputEntry.getIsShow())) {
                switch (Objects.requireNonNull(FormType.of(inputEntry.getType()))) {
                    case INPUT:
                        params.add(getInputParam(inputEntry));
                        break;
                    case SELECT:
                        params.add(getSelectParam(inputEntry));
                        break;
                    case TEXTAREA:
                        params.add(getTextareaParam(inputEntry));
                        break;
                    case GROUP:
                        params.add(getGroupParam(inputEntry));
                        break;
                    default:
                        break;
                }
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String result = null;

        try {
            result = mapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            log.error("Json parse error.", e);
        }

        return result;
    }

    private InputParam getTextareaParam(DqRuleInputEntry inputEntry) {

        InputParamProps paramProps =
                new InputParamProps();
        paramProps.setDisabled(!inputEntry.getCanEdit());
        paramProps.setSize(SMALL);
        paramProps.setType(PropsType.TEXTAREA.getPropsType());
        paramProps.setRows(1);

        return InputParam
                .newBuilder(inputEntry.getField(), inputEntry.getTitle())
                .addValidate(Validate.newBuilder()
                        .setRequired(inputEntry.getIsValidate())
                        .build())
                .setProps(paramProps)
                .setValue(inputEntry.getData())
                .setPlaceholder(inputEntry.getPlaceholder())
                .setEmit(Boolean.TRUE.equals(inputEntry.getIsEmit()) ? Collections.singletonList(CHANGE) : null)
                .build();
    }

    private SelectParam getSelectParam(DqRuleInputEntry inputEntry) {
        List<ParamsOptions> options = null;

        switch (OptionSourceType.of(inputEntry.getOptionSourceType())) {
            case DEFAULT:
                String optionStr = inputEntry.getOptions();
                if (StringUtils.isNotEmpty(optionStr)) {
                    options = JSONUtils.toList(optionStr, ParamsOptions.class);
                }
                break;
            case DATASOURCE_TYPE:
                options = new ArrayList<>();
                ParamsOptions paramsOptions = null;
                for (DbType dbtype : DbType.values()) {
                    paramsOptions = new ParamsOptions(dbtype.name(), dbtype.getCode(), false);
                    options.add(paramsOptions);
                }
                break;
            case COMPARISON_TYPE:
                options = new ArrayList<>();
                ParamsOptions comparisonOptions = null;
                List<DqComparisonType> list =
                        dqComparisonTypeMapper.selectList(new QueryWrapper<DqComparisonType>().orderByAsc("id"));

                for (DqComparisonType type : list) {
                    comparisonOptions = new ParamsOptions(type.getType(), type.getId(), false);
                    options.add(comparisonOptions);
                }
                break;
            default:
                break;
        }

        return SelectParam
                .newBuilder(inputEntry.getField(), inputEntry.getTitle())
                .setOptions(options)
                .setValue(inputEntry.getData())
                .setSize(SMALL)
                .setPlaceHolder(inputEntry.getPlaceholder())
                .setEmit(Boolean.TRUE.equals(inputEntry.getIsEmit()) ? Collections.singletonList(CHANGE) : null)
                .build();
    }

    private InputParam getInputParam(DqRuleInputEntry inputEntry) {
        InputParamProps paramProps =
                new InputParamProps();
        paramProps.setDisabled(!inputEntry.getCanEdit());
        paramProps.setSize(SMALL);
        paramProps.setRows(2);

        return InputParam
                .newBuilder(inputEntry.getField(), inputEntry.getTitle())
                .addValidate(Validate.newBuilder()
                        .setRequired(inputEntry.getIsValidate())
                        .build())
                .setProps(paramProps)
                .setValue(inputEntry.getData())
                .setPlaceholder(inputEntry.getPlaceholder())
                .setEmit(Boolean.TRUE.equals(inputEntry.getIsEmit()) ? Collections.singletonList(CHANGE) : null)
                .build();
    }

    private GroupParam getGroupParam(DqRuleInputEntry inputEntry) {
        return GroupParam
                .newBuilder(inputEntry.getField(), inputEntry.getTitle())
                .addValidate(Validate.newBuilder()
                        .setRequired(inputEntry.getIsValidate())
                        .build())
                .setProps(new GroupParamsProps().setRules(JSONUtils.toList(inputEntry.getOptions(), PluginParams.class))
                        .setFontSize(20))
                .setEmit(Boolean.TRUE.equals(inputEntry.getIsEmit()) ? Collections.singletonList(CHANGE) : null)
                .build();
    }
}
