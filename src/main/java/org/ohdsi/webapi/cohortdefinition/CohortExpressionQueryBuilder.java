/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ohdsi.webapi.cohortdefinition;

import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;
import org.ohdsi.webapi.helper.ResourceHelper;

/**
 *
 * @author cknoll1
 */
public class CohortExpressionQueryBuilder implements ICohortExpressionElementVisitor {

  private final static String COHORT_QUERY_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/generateCohort.sql");

  private final static String CODESET_QUERY_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/codesetQuery.sql");
  private final static String TARGET_CODESET_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/targetCodeset.sql");
  private final static String EXCLUDE_CODESET_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/excludeCodeset.sql");
  private final static String DESCENDANT_CONCEPTS_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/descendantConcepts.sql");

  private final static String PRIMARY_EVENTS_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/primaryEventsQuery.sql");

  private final static String ADDITIONAL_CRITERIA_TEMMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/additionalCriteria.sql");
  private final static String GROUP_QUERY_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/groupQuery.sql");
  
  private final static String CONDITION_OCCURRENCE_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/conditionOccurrence.sql");
  private final static String DRUG_EXPOSURE_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/drugExposure.sql");
  private final static String PROCEDURE_OCCURRENCE_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/procedureOccurrence.sql");
  private final static String MEASUREMENT_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/measurement.sql");;
  private final static String OBSERVATION_PERIOD_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/observationPeriod.sql");;
  private final static String OBSERVATION_TEMPLATE = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/observation.sql");;

  private ArrayList<Integer> getConceptIdsFromConcepts(Concept[] concepts) {
    ArrayList<Integer> conceptIdList = new ArrayList<>();
    for (Concept concept : concepts) {
      conceptIdList.add(concept.id);
    }
    return conceptIdList;
  }

  private String getOperator(String op)
  {
    switch(op)
    {
      case "lt": return "<";
      case "lte" : return "<=";
      case "eq": return "=";
      case "!eq": return "<>";
      case "gt": return ">";
      case "gte": return ">=";
    }
    throw new RuntimeException("Unknown operator type: " + op);
  }
  
  private String getOccurrenceOperator(int type)
  {
    // Occurance check { id: 0, name: 'Exactly', id: 1, name: 'At Most' }, { id: 2, name: 'At Least' }
    switch (type)
    {
      case 0: return "=";
      case 1: return "<=";
      case 2: return ">=";
    }

    // recieved an unknown operator value
    return "??";
  }
  
  private String getOperator(DateRange range)
  {
    return getOperator(range.op);
  }
  
  private String getOperator(NumericRange range)
  {
    return getOperator(range.op);
  }
  
  private String buildDateRangeClause(String sqlExpression, DateRange range)
  {
    String clause = null;
    if (range.op.endsWith("bt")) // range with a 'between' op
    {
      clause = String.format("%s %sbetween '%s' and '%s'",
          sqlExpression,
          range.op.startsWith("!") ? "not " : "",
          range.value,
          range.extent);
    }
    else // single value range (less than/eq/greater than, etc)
    {
      clause = String.format("%s %s '%s'", sqlExpression, getOperator(range), range.value);
    }
    return clause;
  }
  
  // Assumes integer numeric range
  private String buildNumericRangeClause(String sqlExpression, NumericRange range)
  {
    String clause = null;
    if (range.op.endsWith("bt"))
    {
      clause = String.format("%s %sbetween %d and %d",
        sqlExpression,
        range.op.startsWith("!") ? "not " : "",
        range.value.intValue(),
        range.extent.intValue());
    }
    else
    {
      clause = String.format("%s %s %d", sqlExpression, getOperator(range), range.value.intValue());
    }
    return clause;
  }
 
  // assumes decimal range
  private String buildNumericRangeClause(String sqlExpression, NumericRange range, String format)
  {
    String clause = null;
    if (range.op.endsWith("bt"))
    {
      clause = String.format("%s %sbetween %" + format + " and %" + format,
        sqlExpression,
        range.op.startsWith("!") ? "not " : "",
        range.value.doubleValue(),
        range.value.doubleValue());
    }
    else
    {
      clause = String.format("%s %s %" + format, sqlExpression, getOperator(range), range.value.doubleValue());
    }
    return clause;
  }

  
  private String buildTextFilterClause(String sqlExpression, TextFilter filter)
  {
      String negation = filter.op.startsWith("!") ? "not" : "";
      String prefix = filter.op.endsWith("startsWith") || filter.op.endsWith("contains") ? "%" : "";
      String value = filter.text;
      String postfix = filter.op.endsWith("endsWith") || filter.op.endsWith("contains") ? "%" : "";
      
      return String.format("%s %s like '%s%s%s'", sqlExpression, negation, prefix, value, postfix);
  }
  
  
  private String getCodesetQuery(Codeset[] codesets) {
    String codesetQuery = "";
    
    if (codesets.length > 0) {
      codesetQuery = CODESET_QUERY_TEMPLATE;
      ArrayList<String> codesetQueries = new ArrayList<>();
      for (Codeset cs : codesets) {
        if (cs.targetConcepts.length == 0) {
          continue; // skip codesets that do not have targets
        }
        // construct main target codeset query
        String targetCodesetQuery = StringUtils.replace(TARGET_CODESET_TEMPLATE, "@codesetId", Integer.toString(cs.id));
        ArrayList<Integer> conceptIdList = getConceptIdsFromConcepts(cs.targetConcepts);
        String conceptIds = StringUtils.join(conceptIdList, ",");
        targetCodesetQuery = StringUtils.replace(targetCodesetQuery, "@conceptIds", conceptIds);

        // add descendent concepts (if specificed).  we always replace @descendantQuery with some value, "" if not used.
        String descendantQuery = "";
        if (cs.useDescendents == true) {
          descendantQuery = StringUtils.replace(DESCENDANT_CONCEPTS_TEMPLATE, "@conceptIds", conceptIds);
        }
        targetCodesetQuery = StringUtils.replace(targetCodesetQuery, "@descendantQuery", descendantQuery);

        if (cs.excluded.length > 0) {
          String excludeCodesetQuery = EXCLUDE_CODESET_TEMPLATE;
          ArrayList<Integer> excludeIdList = getConceptIdsFromConcepts(cs.excluded);
          String excludeIds = StringUtils.join(excludeIdList, ",");
          excludeCodesetQuery = StringUtils.replace(excludeCodesetQuery, "@conceptIds", excludeIds);
          String excludeDescendantQuery = "";
          if (cs.excludeDescendents == true)
          {
            excludeDescendantQuery = StringUtils.replace(DESCENDANT_CONCEPTS_TEMPLATE, "@conceptIds", excludeIds);
          }
          excludeCodesetQuery = StringUtils.replace(excludeCodesetQuery, "@descendantQuery", excludeDescendantQuery);
          
          // concatinate target query with exclude query making the target query left join to excluded, looking for join being NULL.
          targetCodesetQuery = targetCodesetQuery + excludeCodesetQuery;
        }
        codesetQueries.add(targetCodesetQuery);
      }
      codesetQuery = StringUtils.replace(codesetQuery, "@codesetQueries", StringUtils.join(codesetQueries, "\nUNION\n"));
    }
    return codesetQuery;
  }
 
  public String getPrimaryEventsQuery(PrimaryCriteria primaryCriteria) {
    String query = PRIMARY_EVENTS_TEMPLATE;
    
    ArrayList<String> criteriaQueries = new ArrayList<>();
    
    for (Criteria c : primaryCriteria.criteriaList)
    {
      criteriaQueries.add(c.accept(this));
    }
    
    query = StringUtils.replace(query,"@criteriaQueries", StringUtils.join(criteriaQueries, "\nUNION\n"));
    
    ArrayList<String> primaryEventsFilters = new ArrayList<>();
    primaryEventsFilters.add(String.format(
        "DATEADD(day,%d,OP.OBSERVATION_PERIOD_START_DATE) <= P.START_DATE AND DATEADD(day,%d,P.START_DATE) <= OP.OBSERVATION_PERIOD_END_DATE",
        primaryCriteria.observationWindow.priorDays,
        primaryCriteria.observationWindow.postDays
      )
    );
    
    query = StringUtils.replace(query, "@EventSort", (primaryCriteria.limit.type != null && primaryCriteria.limit.type.equalsIgnoreCase("LAST")) ? "DESC" : "ASC");
    
    if (!primaryCriteria.limit.type.equalsIgnoreCase("ALL"))
    {
      primaryEventsFilters.add("P.ordinal = 1");
    }
    query = StringUtils.replace(query,"@primaryEventsFilter", StringUtils.join(primaryEventsFilters," AND "));
    
    return query;
  }
  
  public String buildExpressionQuery(CohortExpression expression) {
    String resultSql = COHORT_QUERY_TEMPLATE;

    String codesetQuery = getCodesetQuery(expression.codesets);
    String primaryEventsQuery = getPrimaryEventsQuery(expression.primaryCriteria);
    
    resultSql = StringUtils.replace(resultSql, "@codesetQuery", codesetQuery);
    resultSql = StringUtils.replace(resultSql, "@primaryEventsQuery", primaryEventsQuery);
    
    String additionalCriteriaQuery = "";
    if (expression.additionalCriteria != null)
    {
       additionalCriteriaQuery = "\nINTERSECT\n" + expression.additionalCriteria.accept(this);
    }
    resultSql = StringUtils.replace(resultSql, "@additionalCriteriaQuery", additionalCriteriaQuery);

    resultSql = StringUtils.replace(resultSql, "@EventSort", (expression.limit.type != null && expression.limit.type.equalsIgnoreCase("LAST")) ? "DESC" : "ASC");
    
    if (expression.limit.type != null && !expression.limit.type.equalsIgnoreCase("ALL"))
    {
      resultSql = StringUtils.replace(resultSql, "@ResultLimitFilter","WHERE Results.ordinal = 1");
    }
    else
      resultSql = StringUtils.replace(resultSql, "@ResultLimitFilter","");
    
    //TODO: what should cohortID be?
    resultSql = StringUtils.replace(resultSql, "@cohortId", "-1");
    return resultSql;
  }

// <editor-fold defaultstate="collapsed" desc="ICohortExpressionVisitor implementation">
  @Override
  public String visit(ConditionOccurrence criteria)
  {
    String query = CONDITION_OCCURRENCE_TEMPLATE;
    
    String codesetClause = "";
    if (criteria.codesetId != null)
    {
      codesetClause = String.format("where co.condition_concept_id in (SELECT concept_id from  #Codesets where codeset_id = %d)", criteria.codesetId);
    }
    query = StringUtils.replace(query, "@codesetClause",codesetClause);
    
    ArrayList<String> whereClauses = new ArrayList<>();
    
    // first
    if (criteria.first != null && criteria.first == true)
      whereClauses.add("C.ordinal = 1");

    // occurrenceStartDate
    if (criteria.occurrenceStartDate != null)
    {
      whereClauses.add(buildDateRangeClause("condition_start_date",criteria.occurrenceStartDate));
    }

    // occurrenceEndDate
    if (criteria.occurrenceEndDate != null)
    {
      whereClauses.add(buildDateRangeClause("condition_end_date",criteria.occurrenceEndDate));
    }
    
    // conditionType
    if (criteria.conditionType != null && criteria.conditionType.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.conditionType);
      whereClauses.add(String.format("C.condition_type_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }
    
    // Stop Reason
    if (criteria.stopReason != null)
    {
      whereClauses.add(buildTextFilterClause("C.stop_reason",criteria.stopReason));
    }
    
    // conditionSourceConcept
    if (criteria.conditionSourceConcept != null)
    {
      whereClauses.add(String.format("C.condition_source_concept_id in (SELECT concept_id from #Codesets where codeset_id = %d)", criteria.conditionSourceConcept));
    }
    
    // age
    if (criteria.age != null)
    {
      whereClauses.add(buildNumericRangeClause("DATEPART(year, C.condition_start_date) - P.year_of_birth", criteria.age));
    }
    
    // gender
    if (criteria.gender != null && criteria.gender.length > 0)
    {
      whereClauses.add(String.format("P.gender_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.gender),",")));
    }
    
    // providerSpecialty
    if (criteria.providerSpecialty != null && criteria.providerSpecialty.length > 0)
    {
      whereClauses.add(String.format("PR.specialty_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.providerSpecialty),",")));
    }
    
    // visitType
    if (criteria.visitType != null && criteria.visitType.length > 0)
    {
      whereClauses.add(String.format("V.visit_type_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.visitType),",")));
    }

    String whereClause = "";
    if (whereClauses.size() > 0)
      whereClause = "WHERE " + StringUtils.join(whereClauses, "\nAND ");
    query = StringUtils.replace(query, "@whereClause",whereClause);
    return query;
  }
  
  @Override
  public String visit(DrugExposure criteria)
  {
    String query = DRUG_EXPOSURE_TEMPLATE;

    String codesetClause = "";
    if (criteria.codesetId != null)
    {
      codesetClause = String.format("where de.drug_concept_id in (SELECT concept_id from  #Codesets where codeset_id = %d)", criteria.codesetId);
    }
    query = StringUtils.replace(query, "@codesetClause",codesetClause);
    
    ArrayList<String> whereClauses = new ArrayList<>();
    // first
    if (criteria.first != null && criteria.first == true)
      whereClauses.add("C.ordinal = 1");
    
    // occurrenceStartDate
    if (criteria.occurrenceStartDate != null)
    {
      whereClauses.add(buildDateRangeClause("C.drug_exposure_start_date",criteria.occurrenceStartDate));
    }

    // occurrenceEndDate
    if (criteria.occurrenceEndDate != null)
    {
      whereClauses.add(buildDateRangeClause("C.drug_exposure_end_date",criteria.occurrenceEndDate));
    }

    // drugType
    if (criteria.drugType != null && criteria.drugType.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.drugType);
      whereClauses.add(String.format("C.drug_type_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }
    
    // Stop Reason
    if (criteria.stopReason != null)
    {
      whereClauses.add(buildTextFilterClause("C.stop_reason",criteria.stopReason));
    }

    // refills
    if (criteria.refills != null)
    {
      whereClauses.add(buildNumericRangeClause("C.refills",criteria.refills));
    }

    // quantity
    if (criteria.quantity != null)
    {
      whereClauses.add(buildNumericRangeClause("C.quantity",criteria.quantity,".4f"));
    }

    // days supply
    if (criteria.daysSupply != null)
    {
      whereClauses.add(buildNumericRangeClause("C.days_supply",criteria.daysSupply));
    }

    // routeConcept
    if (criteria.routeConcept != null && criteria.routeConcept.length > 0)
    {
      whereClauses.add(String.format("C.route_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.routeConcept),",")));
    }
    
    // effectiveDrugDose
    if (criteria.effectiveDrugDose != null)
    {
      whereClauses.add(buildNumericRangeClause("C.effective_drug_dose",criteria.effectiveDrugDose,".4f"));
    }

    // doseUnit
    if (criteria.doseUnit != null && criteria.doseUnit.length > 0)
    {
      whereClauses.add(String.format("C.dose_unit_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.doseUnit),",")));
    }

    // LotNumber
    if (criteria.lotNumber != null)
    {
      whereClauses.add(buildTextFilterClause("C.lot_number", criteria.lotNumber));
    }
    
    // drugSourceConcept
    if (criteria.drugSourceConcept != null)
    {
      whereClauses.add(String.format("C.drug_source_concept_id in (SELECT concept_id from #Codesets where codeset_id = %d)", criteria.drugSourceConcept));
    }    
    
    // age
    if (criteria.age != null)
    {
      whereClauses.add(buildNumericRangeClause("DATEPART(year, C.drug_exposure_start_date) - P.year_of_birth", criteria.age));
    }
    
    // gender
    if (criteria.gender != null && criteria.gender.length > 0)
    {
      whereClauses.add(String.format("P.gender_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.gender),",")));
    }
    
    // providerSpecialty
    if (criteria.providerSpecialty != null && criteria.providerSpecialty.length > 0)
    {
      whereClauses.add(String.format("PR.specialty_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.providerSpecialty),",")));
    }
    
    // visitType
    if (criteria.visitType != null && criteria.visitType.length > 0)
    {
      whereClauses.add(String.format("V.visit_type_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.visitType),",")));
    }
    
    String whereClause = "";
    if (whereClauses.size() > 0)
      whereClause = "WHERE " + StringUtils.join(whereClauses, "\nAND ");
    query = StringUtils.replace(query, "@whereClause",whereClause);
    
    return query;
  }  
  
  @Override
  public String visit(ProcedureOccurrence criteria)
  {
    String query = PROCEDURE_OCCURRENCE_TEMPLATE;
    
    String codesetClause = "";
    if (criteria.codesetId != null)
    {
      codesetClause = String.format("where po.procedure_concept_id in (SELECT concept_id from  #Codesets where codeset_id = %d)", criteria.codesetId);
    }
    query = StringUtils.replace(query, "@codesetClause",codesetClause);
    
    ArrayList<String> whereClauses = new ArrayList<>();
    
    // first
    if (criteria.first != null && criteria.first == true)
      whereClauses.add("C.ordinal = 1");

    // occurrenceStartDate
    if (criteria.occurrenceStartDate != null)
    {
      whereClauses.add(buildDateRangeClause("C.procedure_date",criteria.occurrenceStartDate));
    }    
    
    // procedureType
    if (criteria.procedureType != null && criteria.procedureType.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.procedureType);
      whereClauses.add(String.format("C.procedure_type_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }
    
    // modifier
    if (criteria.modifier != null && criteria.modifier.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.modifier);
      whereClauses.add(String.format("C.modifier_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }

    // quantity
    if (criteria.quantity != null)
    {
      whereClauses.add(buildNumericRangeClause("C.quantity",criteria.quantity));
    }
    
    // procedureSourceConcept
    if (criteria.procedureSourceConcept != null)
    {
      whereClauses.add(String.format("C.procedure_source_concept_id in (SELECT concept_id from #Codesets where codeset_id = %d)", criteria.procedureSourceConcept));
    }
    
    // age
    if (criteria.age != null)
    {
      whereClauses.add(buildNumericRangeClause("DATEPART(year, C.procedure_date) - P.year_of_birth", criteria.age));
    }
    
    // gender
    if (criteria.gender != null && criteria.gender.length > 0)
    {
      whereClauses.add(String.format("P.gender_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.gender),",")));
    }
    
    // providerSpecialty
    if (criteria.providerSpecialty != null && criteria.providerSpecialty.length > 0)
    {
      whereClauses.add(String.format("PR.specialty_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.providerSpecialty),",")));
    }
    
    // visitType
    if (criteria.visitType != null && criteria.visitType.length > 0)
    {
      whereClauses.add(String.format("V.visit_type_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.visitType),",")));
    }

    
    String whereClause = "";
    if (whereClauses.size() > 0)
      whereClause = "WHERE " + StringUtils.join(whereClauses, "\nAND ");
    query = StringUtils.replace(query, "@whereClause",whereClause);
    
    return query;
  }
  
  @Override
  public String visit(Measurement criteria)
  {
    String query = MEASUREMENT_TEMPLATE;
    
    String codesetClause = "";
    if (criteria.codesetId != null)
    {
      codesetClause = String.format("where m.measurement_concept_id in (SELECT concept_id from  #Codesets where codeset_id = %d)", criteria.codesetId);
    }
    query = StringUtils.replace(query, "@codesetClause",codesetClause);
    
    ArrayList<String> whereClauses = new ArrayList<>();
  
    // first
    if (criteria.first != null && criteria.first == true)
      whereClauses.add("C.ordinal = 1");

    // occurrenceStartDate
    if (criteria.occurrenceStartDate != null)
    {
      whereClauses.add(buildDateRangeClause("C.measurement_date",criteria.occurrenceStartDate));
    }        
  
    // measurementType
    if (criteria.measurementType != null && criteria.measurementType.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.measurementType);
      whereClauses.add(String.format("C.measurement_type_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }
    
    // operator
    if (criteria.operator != null && criteria.operator.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.operator);
      whereClauses.add(String.format("C.operator_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }
    
    // valueAsNumber
    if (criteria.valueAsNumber != null)
    {
      whereClauses.add(buildNumericRangeClause("C.value_as_number",criteria.valueAsNumber,".4f"));
    }
    
    // valueAsConcept
    if (criteria.valueAsConcept != null && criteria.valueAsConcept.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.valueAsConcept);
      whereClauses.add(String.format("C.value_as_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }    
    
    // unit
    if (criteria.unit != null && criteria.unit.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.valueAsConcept);
      whereClauses.add(String.format("C.unit_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }
    
    // rangeLow
    if (criteria.rangeLow != null)
    {
      whereClauses.add(buildNumericRangeClause("C.range_low",criteria.rangeLow,".4f"));
    }

    // rangeHigh
    if (criteria.rangeHigh != null)
    {
      whereClauses.add(buildNumericRangeClause("C.range_high",criteria.rangeHigh,".4f"));
    }
    
    // rangeLowRatio
    if (criteria.rangeLowRatio != null)
    {
      whereClauses.add(buildNumericRangeClause("(C.value_as_number / C.range_low)",criteria.rangeLowRatio,".4f"));
    }

    // rangeHighRatio
    if (criteria.rangeHighRatio != null)
    {
      whereClauses.add(buildNumericRangeClause("(C.value_as_number / C.range_high)",criteria.rangeHighRatio,".4f"));
    }
    
    // measurementSourceConcept
    if (criteria.measurementSourceConcept != null)
    {
      whereClauses.add(String.format("C.measurement_source_concept_id in (SELECT concept_id from #Codesets where codeset_id = %d)", criteria.measurementSourceConcept));
    }
    
    // age
    if (criteria.age != null)
    {
      whereClauses.add(buildNumericRangeClause("DATEPART(year, C.measurement_date) - P.year_of_birth", criteria.age));
    }
    
    // gender
    if (criteria.gender != null && criteria.gender.length > 0)
    {
      whereClauses.add(String.format("P.gender_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.gender),",")));
    }
    
    // providerSpecialty
    if (criteria.providerSpecialty != null && criteria.providerSpecialty.length > 0)
    {
      whereClauses.add(String.format("PR.specialty_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.providerSpecialty),",")));
    }
    
    // visitType
    if (criteria.visitType != null && criteria.visitType.length > 0)
    {
      whereClauses.add(String.format("V.visit_type_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.visitType),",")));
    }
    
    String whereClause = "";
    if (whereClauses.size() > 0)
      whereClause = "WHERE " + StringUtils.join(whereClauses, "\nAND ");
    query = StringUtils.replace(query, "@whereClause",whereClause);
    
    return query;
  }
  
  public String visit(ObservationPeriod criteria)
  {
    String query = OBSERVATION_PERIOD_TEMPLATE;

    ArrayList<String> whereClauses = new ArrayList<>();

    if (criteria.first != null && criteria.first == true)
      whereClauses.add("C.ordinal = 1");
    
    // periodStartDate
    if (criteria.periodStartDate != null)
    {
      whereClauses.add(buildDateRangeClause("C.observation_period_start_date",criteria.periodStartDate));
    }        

    // periodEndDate
    if (criteria.periodEndDate != null)
    {
      whereClauses.add(buildDateRangeClause("C.observation_period_end_date",criteria.periodEndDate));
    }        
    
    // periodType
    if (criteria.periodType != null && criteria.periodType.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.periodType);
      whereClauses.add(String.format("C.period_type_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }
    
    // periodLength
    if (criteria.periodLength != null)
    {
      whereClauses.add(buildNumericRangeClause("DATEDIFF(d,C.observation_period_start_date, C.observation_period_end_date)", criteria.periodLength));
    }      

    // ageAtStart
    if (criteria.ageAtStart != null)
    {
      whereClauses.add(buildNumericRangeClause("DATEPART(year, C.observation_period_start_date) - P.year_of_birth", criteria.ageAtStart));
    }

    // ageAtEnd
    if (criteria.ageAtEnd != null)
    {
      whereClauses.add(buildNumericRangeClause("DATEPART(year, C.observation_period_end_date) - P.year_of_birth", criteria.ageAtEnd));
    }
    
    String whereClause = "";
    if (whereClauses.size() > 0)
      whereClause = "WHERE " + StringUtils.join(whereClauses, "\nAND ");
    query = StringUtils.replace(query, "@whereClause",whereClause);
    
    return query;
  }
  
@Override
  public String visit(Observation criteria)
  {
    String query = OBSERVATION_TEMPLATE;
    
    String codesetClause = "";
    if (criteria.codesetId != null)
    {
      codesetClause = String.format("where o.observation_concept_id in (SELECT concept_id from  #Codesets where codeset_id = %d)", criteria.codesetId);
    }
    query = StringUtils.replace(query, "@codesetClause",codesetClause);
    
    ArrayList<String> whereClauses = new ArrayList<>();
  
    // first
    if (criteria.first != null && criteria.first == true)
      whereClauses.add("C.ordinal = 1");

    // occurrenceStartDate
    if (criteria.occurrenceStartDate != null)
    {
      whereClauses.add(buildDateRangeClause("C.observation_date",criteria.occurrenceStartDate));
    }        
  
    // measurementType
    if (criteria.observationType != null && criteria.observationType.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.observationType);
      whereClauses.add(String.format("C.observation_type_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }
       
    // valueAsNumber
    if (criteria.valueAsNumber != null)
    {
      whereClauses.add(buildNumericRangeClause("C.value_as_number",criteria.valueAsNumber,".4f"));
    }
    
    // valueAsString
    if (criteria.valueAsString != null)
    {
      whereClauses.add(buildTextFilterClause("C.value_as_string",criteria.valueAsString));
    }

    // valueAsConcept
    if (criteria.valueAsConcept != null && criteria.valueAsConcept.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.valueAsConcept);
      whereClauses.add(String.format("C.value_as_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }    
    
    // qualifier
    if (criteria.qualifier != null && criteria.qualifier.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.qualifier);
      whereClauses.add(String.format("C.qualifier_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }
    
    // unit
    if (criteria.unit != null && criteria.unit.length > 0)
    {
      ArrayList<Integer> conceptIds = getConceptIdsFromConcepts(criteria.valueAsConcept);
      whereClauses.add(String.format("C.unit_concept_id in (%s)", StringUtils.join(conceptIds, ",")));
    }
       
    // observationSourceConcept
    if (criteria.observationSourceConcept != null)
    {
      whereClauses.add(String.format("C.observation_source_concept_id in (SELECT concept_id from #Codesets where codeset_id = %d)", criteria.observationSourceConcept));
    }
    
    // age
    if (criteria.age != null)
    {
      whereClauses.add(buildNumericRangeClause("DATEPART(year, C.observation_date) - P.year_of_birth", criteria.age));
    }
    
    // gender
    if (criteria.gender != null && criteria.gender.length > 0)
    {
      whereClauses.add(String.format("P.gender_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.gender),",")));
    }
    
    // providerSpecialty
    if (criteria.providerSpecialty != null && criteria.providerSpecialty.length > 0)
    {
      whereClauses.add(String.format("PR.specialty_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.providerSpecialty),",")));
    }
    
    // visitType
    if (criteria.visitType != null && criteria.visitType.length > 0)
    {
      whereClauses.add(String.format("V.visit_type_concept_id in (%s)", StringUtils.join(getConceptIdsFromConcepts(criteria.visitType),",")));
    }
    
    String whereClause = "";
    if (whereClauses.size() > 0)
      whereClause = "WHERE " + StringUtils.join(whereClauses, "\nAND ");
    query = StringUtils.replace(query, "@whereClause",whereClause);
    
    return query;
  }  
  
  @Override
  public String visit(AdditionalCriteria additionalCriteria)
  {
    String query = ADDITIONAL_CRITERIA_TEMMPLATE;
    
    String criteriaQuery = additionalCriteria.criteria.accept(this);
    query = StringUtils.replace(query,"@criteriaQuery",criteriaQuery);
    
    // build index date window expression
    Window startWindow = additionalCriteria.startWindow;
    String startExpression;
    String endExpression;
    
    if (startWindow.start.days != null)
      startExpression = String.format("DATEADD(day,%d,P.START_DATE)", startWindow.start.coeff * startWindow.start.days);
    else
      startExpression = startWindow.start.coeff == -1 ? "P.OP_START_DATE" : "P.OP_END_DATE";
    
    if (startWindow.end.days != null)
      endExpression = String.format("DATEADD(day,%d,P.START_DATE)", startWindow.end.coeff * startWindow.end.days);
    else
      endExpression = startWindow.end.coeff == -1 ? "P.OP_START_DATE" : "P.OP_END_DATE";
    
    String windowCriteria = String.format("WHERE A.START_DATE BETWEEN %s and %s", startExpression, endExpression);
    query = StringUtils.replace(query,"@windowCriteria",windowCriteria);

    String occurrenceCriteria = String.format(
      "HAVING COUNT(A.PERSON_ID) %s %d", 
      getOccurrenceOperator(additionalCriteria.occurrence.type), 
      additionalCriteria.occurrence.count
    );
    
    query = StringUtils.replace(query, "@occurrenceCriteria", occurrenceCriteria);

    return query;
  }
  
  @Override
  public String visit(CriteriaGroup group) {
    String query = GROUP_QUERY_TEMPLATE;
    ArrayList<String> additionalCriteriaQueries = new ArrayList<>();
    
    for(AdditionalCriteria ac : group.criteriaList)
    {
      additionalCriteriaQueries.add(ac.accept(this));
    }
    
    for(CriteriaGroup g : group.groups)
    {
      additionalCriteriaQueries.add(g.accept(this));      
    }
    
    query = StringUtils.replace(query, "@criteriaQueries", StringUtils.join(additionalCriteriaQueries, group.type.equalsIgnoreCase("ANY") ? "\nUNION\n" : "\nINTERSECT\n"));
    
    return query;    
  }
  
// </editor-fold>
  
}