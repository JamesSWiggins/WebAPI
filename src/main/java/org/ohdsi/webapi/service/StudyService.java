/*
 * Copyright 2017 Observational Health Data Sciences and Informatics [OHDSI.org].
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ohdsi.webapi.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.ohdsi.sql.SqlRender;
import org.ohdsi.sql.SqlTranslate;
import org.ohdsi.webapi.helper.ResourceHelper;
import org.ohdsi.webapi.shiro.management.Security;
import org.ohdsi.webapi.source.Source;
import org.ohdsi.webapi.source.SourceDaimon;
import org.ohdsi.webapi.study.CohortSet;
import org.ohdsi.webapi.study.CohortSetRepository;
import org.ohdsi.webapi.study.Concept;
import org.ohdsi.webapi.study.Study;
import org.ohdsi.webapi.study.StudyCohort;
import org.ohdsi.webapi.study.StudyRepository;
import org.ohdsi.webapi.util.SessionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author Chris Knoll <cknoll@ohdsi.org>
 */
@Path("/study")
@Component
public class StudyService extends AbstractDaoService  {

  private final String QUERY_COVARIATE_STATS  = ResourceHelper.GetResourceAsString("/resources/study/sql/queryCovariateStats.sql");
  private final String QUERY_COVARIATE_STATS_VOCAB  = ResourceHelper.GetResourceAsString("/resources/study/sql/queryCovariateStatsVocab.sql");
  private final String QUERY_COVARIATE_DIST  = ResourceHelper.GetResourceAsString("/resources/study/sql/queryCovariateDist.sql");
  private final String QUERY_COVARIATE_DIST_VOCAB  = ResourceHelper.GetResourceAsString("/resources/study/sql/queryCovariateDistVocab.sql");
  private final String QUERY_COHORTSETS  = ResourceHelper.GetResourceAsString("/resources/study/sql/queryCohortSets.sql");
  private final String QUERY_DASHBOARD  = ResourceHelper.GetResourceAsString("/resources/study/sql/queryDashboard.sql");
  private final String QUERY_DASHBOARD_VOCAB  = ResourceHelper.GetResourceAsString("/resources/study/sql/queryDashboardVocab.sql");
  private final String QUERY_DASHBOARD_OUTCOMES  = ResourceHelper.GetResourceAsString("/resources/study/sql/queryDashboardOutcomes.sql");

  @Autowired
  StudyRepository studyRepository;
	
	@Autowired
	CohortSetRepository cohortSetRepository;

  @Autowired
  private Security security;
  
  @PersistenceContext
  protected EntityManager entityManager;
  
  public static class StudyListItem {
    public Integer id;
    public String name;
    public String description;
    
  }
  
  public static class StudyDTO extends StudyListItem {
    public List<CohortDetail> cohorts;
    public List<StudyIRDTO> irAnalysisList;
    public List<StudyCCADTO> ccaList;
    public List<StudySCCDTO> sccList;
    public List<StudySourceDTO> sources;
    
    public StudyDTO () {}
  }
  
  public static class StudyIRDTO {
    
    public Integer id;
    public String params;
    public List<Long> targets;
    public List<Long> outcomes;
    
    public StudyIRDTO() {
    }
    
  }
  
  public static class SCCPair {
    public Long target;
    public Long outcome;
    public List<Long> negativeControls;
  }
  public static class StudySCCDTO {
    public Integer id;
    public String params;
    public List<SCCPair> pairs;
    
    public StudySCCDTO() {}
  }
  
  public static class CCATrio {
    public Long target;
    public Long comaprator;
    public Long outcome;
    public List<Long> negativeControls;
    
    public CCATrio () {}
    
  }

  public static class StudyCCADTO {
    public Integer id;
    public String params;
    public List<CCATrio> trios;
    
    public StudyCCADTO() {}
  }

  public static class StudySourceDTO {
   public int sourceId;
   public String name;
   
   public StudySourceDTO() {}
   
   public StudySourceDTO (int sourceId, String name) {
     this.sourceId = sourceId;
     this.name = name;
   }
  }
    
  public static class CohortRelationship {
    public long target;
    public org.ohdsi.webapi.study.RelationshipType relationshipType;
  }

  public static class CohortDetail {
    public long cohortId;
    public String name;
    public String expression;
    public List<Concept> concepts = new ArrayList<>();
    public List<CohortRelationship> relationships = new ArrayList<>();
  }
  
  
  public static class CovariateStat {
    public long covariateId;
    public String covariateName;
    public long analysisId;
    public String analysisName;
    public String domainId;
    public String timeWindow;
    public long conceptId;
    public long countValue;
    public BigDecimal statValue;
		public long distance = 0;
  }

  public static class CovariateDist {
    public long covariateId;
    public String covariateName;
    public long analysisId;
    public String analysisName;
    public String domainId;
    public String timeWindow;
    public long conceptId;
    public long countValue;
    public BigDecimal avgValue;
    public BigDecimal stdevValue;
    public long minValue;
    public long p10Value;
    public long p25Value;
    public long medianValue;
    public long p75Value;
    public long p90Value;
    public long maxValue;
		public long distance = 0;
  }
  
  public static class CohortSetListItem {
    public int id;
    public String name;
    public String description;
    public int members;
  }

  public static class StudyStatistics {
    public List<CovariateStat> categorical = new ArrayList<>();
    public List<CovariateDist> continuous = new ArrayList<>();
  }

	public static class CohortSetOutcomeItem {
		public long outcomeCohortId;
		public String outcomeCohortName;
		public long outcomeConceptId;
	}

	public static class DashboardItem {
		public long targetCohortId;
		public String targetCohortName;
		public long outcomeCohortId;
		public String outcomeCohortName;
		public long outcomeConceptId;
		public String outcomeConceptName;
		public BigDecimal incidence;
		public BigDecimal estimate;
		public long seriousness;
		public int onLabel;
		public int negativeControl;
		public int requested;
		public int published;
		public int labeled;
		public int distance;
	}
  
  private List<String> buildCriteriaClauses (String searchTerm, List<String> analysisIds, List<String> timeWindows, List<String> domains) {
    ArrayList<String> clauses = new ArrayList<>();
    
    if (searchTerm != null && searchTerm.length() > 0)
    {
      clauses.add(String.format("lower(ar1.covariate_name) like '%%%s%%'",searchTerm));
    }
    
    if (analysisIds != null && analysisIds.size() > 0)
    {
      ArrayList<String> ids = new ArrayList<>();
      ArrayList<String> ranges = new ArrayList<>();
      
      analysisIds.stream().map((analysisIdExpr) -> analysisIdExpr.split(":")).forEachOrdered((parsedIds) -> {
        if (parsedIds.length > 1) {
          ranges.add(String.format("(ar1.analysis_id >= %s and ar1.analysis_id <= %s)", parsedIds[0], parsedIds[1]));
        } else {
          ids.add(parsedIds[0]);
        }
      });
      
      String idClause = "";
      if (ids.size() > 0)
        idClause = String.format("ar1.analysis_id in (%s)", StringUtils.join(ids, ","));
      
      if (ranges.size() > 0)
        idClause += (idClause.length() > 0 ? " OR " : "") + StringUtils.join(ranges, " OR ");
      
      clauses.add("(" + idClause + ")");
    }
  
    if (timeWindows != null && timeWindows.size() > 0) {
      ArrayList<String> timeWindowClauses = new ArrayList<>();
      timeWindows.forEach((timeWindow) -> {
        timeWindowClauses.add(String.format("ar1.time_window = '%s'", timeWindow));
      });
      clauses.add("(" + StringUtils.join(timeWindowClauses, " OR ") + ")" );
    }
    
    if (domains != null && domains.size() > 0) {
			ArrayList<String> domainClauses = new ArrayList<>();
			domains.forEach((domain) -> {
				if (domain.toLowerCase().equals("null")) {
					domainClauses.add("ar1.domain_id is null");					
				} else {
					domainClauses.add(String.format("lower(ar1.domain_id) = lower('%s')", domain));					
				}
			});
			clauses.add("(" + StringUtils.join(domainClauses, " OR ") + ")");
    }
    
    return clauses;
  }
  
  public CohortDetail fromStudyCohort(StudyCohort studyCohort) {
    CohortDetail detail = new CohortDetail();
    detail.cohortId = studyCohort.getId();
    detail.name = studyCohort.getName();
    detail.relationships = studyCohort.getCohortRelationships().stream().map(r -> {
      CohortRelationship rel = new CohortRelationship();
      rel.target = r.getTarget().getId();
      rel.relationshipType = r.getRelationshipType();
      return rel;
    }).collect(Collectors.toList());
    detail.concepts = studyCohort.getConcepts().stream().collect(Collectors.toList());
    return detail;
  }
  
  public StudyDTO fromStudy(Study studyEntity)
  {
    StudyDTO study = new StudyDTO();
    HashMap<Integer, StudyService.CohortDetail> cohorts = new HashMap<>();
    
    study.id = studyEntity.getId();
    study.name = studyEntity.getName();
    study.description = studyEntity.getDescription();
    
    // Map IRAs
    study.irAnalysisList = studyEntity.getIrAnalysisList().stream().map(i -> {
      StudyIRDTO ira = new StudyIRDTO();
      ira.id = i.getId();
      ira.params = i.getParams();
      
      ira.targets = i.getTargets().stream().map(t -> {
        return t.getId();
      }).collect(Collectors.toList());
      
      ira.outcomes = i.getOutcomes().stream().map(o -> {
        return o.getId();
      }).collect(Collectors.toList());

      return ira;
    }).collect(Collectors.toList());
    
    // Map SCCs
    study.sccList = studyEntity.getSccList().stream().map(scc -> {
      StudySCCDTO sccDTO = new StudySCCDTO();
      
      sccDTO.id = scc.getId();
      sccDTO.params = scc.getParams();

      sccDTO.pairs = scc.getPairs().stream().map(pair -> {
        SCCPair pairDTO = new SCCPair();
        
        pairDTO.target = pair.getTarget().getId();
        pairDTO.outcome = pair.getOutcome().getId();
        pairDTO.negativeControls = pair.getNegativeControls().stream().map(StudyCohort::getId).collect(Collectors.toList());
        return pairDTO;
      }).collect(Collectors.toList());
      return sccDTO;
    }).collect(Collectors.toList());
    
    // Map CCAs
    study.ccaList = studyEntity.getCcaList().stream().map(cca -> {
      StudyCCADTO ccaDTO = new StudyCCADTO();
      
      ccaDTO.id = cca.getId();
      ccaDTO.params = cca.getParams();

      ccaDTO.trios = cca.getTrios().stream().map(trio -> {
        CCATrio trioDTO = new CCATrio();
        
        trioDTO.target = trio.getTarget().getId();
        trioDTO.comaprator = trio.getComparator().getId();
        trioDTO.outcome = trio.getOutcome().getId();
        trioDTO.negativeControls = trio.getNegativeControls().stream().map(StudyCohort::getId).collect(Collectors.toList());
        return trioDTO;
      }).collect(Collectors.toList());
      return ccaDTO;
    }).collect(Collectors.toList());

    // Map cohorts
    study.cohorts = studyEntity.getCohortList().stream().map(c -> {
        return fromStudyCohort(c);
    }).collect(Collectors.toList());
    
    // Map Sources
    study.sources = studyEntity.getSourceList().stream().map (s -> {
      return new StudySourceDTO(s.getId(), s.getName());
    }).collect(Collectors.toList());
    
    return study;
  }
  
  
  @GET
  @Path("/")
  @Produces(MediaType.APPLICATION_JSON)
  public List<StudyListItem> getStudyList(){
    Stream<Study> studyStream = StreamSupport.stream(studyRepository.findAll().spliterator(), true);
    return studyStream.map(s -> {
      StudyListItem item = new StudyListItem();
      item.id = s.getId();
      item.name = s.getName();
      item.description = s.getDescription();
      return item;
    }).collect(Collectors.toList());
  }
          
  @GET
  @Path("{studyId}/results/covariates/{cohortId}/{sourceId}")
  @Produces(MediaType.APPLICATION_JSON)
  public StudyStatistics getStudyStatistics(
          @PathParam("studyId") final int studyId, 
          @PathParam("cohortId") final long cohortId, 
          @PathParam("sourceId") final int sourceId,
          @QueryParam("searchTerm") final String searchTerm,
          @QueryParam("analysisId") final List<String> analysisIds,
          @QueryParam("timeWindow") final List<String> timeWindows,
          @QueryParam("domain") final List<String> domains
  ) {  
    StudyStatistics result = new StudyStatistics();
    String translatedSql;
    
    List<String> criteriaClauses = buildCriteriaClauses(searchTerm, analysisIds, timeWindows, domains);
    
    String categoricalQuery = SqlRender.renderSql(
            QUERY_COVARIATE_STATS, 
            new String[] {"study_results_schema", "cohort_definition_id", "source_id", "criteria_clauses"},
            new String[] {this.getStudyResultsSchema(), Long.toString(cohortId), Integer.toString(sourceId), criteriaClauses.isEmpty() ? "" : " AND\n" + StringUtils.join(criteriaClauses, "\n AND ")}
    );
    
    translatedSql = SqlTranslate.translateSql(categoricalQuery, "sql server", this.getStudyResultsDialect(), SessionUtils.sessionId(), this.getStudyResultsSchema());
    List<CovariateStat> categoricalStats =  this.getStudyResultsJdbcTemplate().query(translatedSql, (rs,rowNum) -> { 
      CovariateStat mappedRow = new CovariateStat() {{
        covariateId = rs.getLong("covariate_id");
        covariateName = rs.getString("covariate_name");
        analysisId = rs.getLong("analysis_id");
        analysisName = rs.getString("analysis_name");
        domainId = rs.getString("domain_id");
        timeWindow = rs.getString("time_window");
        conceptId = rs.getLong("concept_id");
        countValue = rs.getLong("count_value");
        statValue = new BigDecimal(rs.getDouble("stat_value")).setScale(5, RoundingMode.DOWN);
      }};
      return mappedRow;
    });    
    
    
    String continuousQuery = SqlRender.renderSql(
            QUERY_COVARIATE_DIST, 
            new String[] {"study_results_schema", "cohort_definition_id", "source_id", "criteria_clauses"},
            new String[] {this.getStudyResultsSchema(), Long.toString(cohortId), Integer.toString(sourceId), criteriaClauses.isEmpty() ? "" : " AND\n" +StringUtils.join(criteriaClauses, "\n AND ")}
    );
    
    translatedSql = SqlTranslate.translateSql(continuousQuery, "sql server", this.getStudyResultsDialect(), SessionUtils.sessionId(), this.getStudyResultsSchema());
    List<CovariateDist> continuousStats =  this.getStudyResultsJdbcTemplate().query(translatedSql, (rs,rowNum) -> { 
      CovariateDist mappedRow = new CovariateDist() {{
        covariateId = rs.getLong("covariate_id");
        covariateName = rs.getString("covariate_name");
        analysisId = rs.getLong("analysis_id");
        analysisName = rs.getString("analysis_name");
        domainId = rs.getString("domain_id");
        timeWindow = rs.getString("time_window");
        conceptId = rs.getLong("concept_id");
        countValue = rs.getLong("count_value");
        avgValue = new BigDecimal(rs.getDouble("avg_value")).setScale(5,RoundingMode.DOWN);
        stdevValue = new BigDecimal(rs.getDouble("stdev_value")).setScale(5,RoundingMode.DOWN);
        minValue = rs.getLong("min_value");
        p10Value = rs.getLong("p10_value");
        p25Value = rs.getLong("p25_value");
        medianValue = rs.getLong("median_value");
        p75Value = rs.getLong("p75_value");
        p90Value = rs.getLong("p90_value");
        maxValue = rs.getLong("max_value");
      }};
      return mappedRow;
    });    
    
    result.categorical = categoricalStats;
    result.continuous = continuousStats;
    
    return result;
  }
	
	@GET
  @Path("{studyId}/results/covariates/{cohortId}/{sourceId}/explore/{covariateId}")
  @Produces(MediaType.APPLICATION_JSON)
  public StudyStatistics getStudyStatisticsByVocab(          
					@PathParam("studyId") final int studyId, 
          @PathParam("cohortId") final long cohortId, 
          @PathParam("sourceId") final int sourceId,
          @PathParam("covariateId") final long covariateId
	){
    StudyStatistics result = new StudyStatistics();
    String translatedSql;
    
    String categoricalQuery = SqlRender.renderSql(
            QUERY_COVARIATE_STATS_VOCAB, 
            new String[] {"study_results_schema", "cohort_definition_id", "source_id", "covariate_id"},
            new String[] {this.getStudyResultsSchema(), Long.toString(cohortId), Integer.toString(sourceId), Long.toString(covariateId)}
    );
    
    translatedSql = SqlTranslate.translateSql(categoricalQuery, "sql server", this.getStudyResultsDialect(), SessionUtils.sessionId(), this.getStudyResultsSchema());
    List<CovariateStat> categoricalStats =  this.getStudyResultsJdbcTemplate().query(translatedSql, (rs,rowNum) -> { 
      CovariateStat mappedRow = new CovariateStat() {{
        covariateId = rs.getLong("covariate_id");
        covariateName = rs.getString("covariate_name");
        analysisId = rs.getLong("analysis_id");
        analysisName = rs.getString("analysis_name");
        domainId = rs.getString("domain_id");
        timeWindow = rs.getString("time_window");
        conceptId = rs.getLong("concept_id");
        countValue = rs.getLong("count_value");
        statValue = new BigDecimal(rs.getDouble("stat_value")).setScale(5, RoundingMode.DOWN);
				distance = rs.getLong("min_levels_of_separation");
      }};
      return mappedRow;
    });    
    
    String continuousQuery = SqlRender.renderSql(
            QUERY_COVARIATE_DIST_VOCAB, 
            new String[] {"study_results_schema", "cohort_definition_id", "source_id", "covariate_id"},
            new String[] {this.getStudyResultsSchema(), Long.toString(cohortId), Integer.toString(sourceId), Long.toString(covariateId)}
    );
    
    translatedSql = SqlTranslate.translateSql(continuousQuery, "sql server", this.getStudyResultsDialect(), SessionUtils.sessionId(), this.getStudyResultsSchema());
    List<CovariateDist> continuousStats =  this.getStudyResultsJdbcTemplate().query(translatedSql, (rs,rowNum) -> { 
      CovariateDist mappedRow = new CovariateDist() {{
        covariateId = rs.getLong("covariate_id");
        covariateName = rs.getString("covariate_name");
        analysisId = rs.getLong("analysis_id");
        analysisName = rs.getString("analysis_name");
        domainId = rs.getString("domain_id");
        timeWindow = rs.getString("time_window");
        conceptId = rs.getLong("concept_id");
        countValue = rs.getLong("count_value");
        avgValue = new BigDecimal(rs.getDouble("avg_value")).setScale(5,RoundingMode.DOWN);
        stdevValue = new BigDecimal(rs.getDouble("stdev_value")).setScale(5,RoundingMode.DOWN);
        minValue = rs.getLong("min_value");
        p10Value = rs.getLong("p10_value");
        p25Value = rs.getLong("p25_value");
        medianValue = rs.getLong("median_value");
        p75Value = rs.getLong("p75_value");
        p90Value = rs.getLong("p90_value");
        maxValue = rs.getLong("max_value");
				distance = rs.getLong("min_levels_of_separation");
      }};
      return mappedRow;
    });    
		
    result.categorical = categoricalStats;
    result.continuous = continuousStats;
    
    return result;
	}
	
  
  @GET
  @Path("{studyId}/cohortset/search")
  @Produces(MediaType.APPLICATION_JSON)
  public List<CohortSetListItem> getCohortSets(
          @PathParam("studyId") final int studyId,
          @QueryParam("searchTerm") final String searchTerm
  ) {
    String translatedSql;
    
    String cohortSetQuery = SqlRender.renderSql(
            QUERY_COHORTSETS, 
            new String[] {"ohdsi_schema", "study_id", "search_term"},
            new String[] {getOhdsiSchema(), Integer.toString(studyId), searchTerm}
    );
    
    translatedSql = SqlTranslate.translateSql(cohortSetQuery, "sql server", this.getDialect(), SessionUtils.sessionId(), null);
    List<CohortSetListItem> cohortSets =  this.getJdbcTemplate().query(translatedSql, (rs,rowNum) -> { 
      CohortSetListItem mappedRow = new CohortSetListItem() {{
        id =  rs.getInt("id");
        name = rs.getString("name");
        description = rs.getString("description");
        members = rs.getInt("members");
      }};
      
      return mappedRow;
      
    });
    
    return cohortSets; 
  }
	
	@GET
  @Path("/{studyId}/cohortset/{cohortSetId}/outcomes")
  @Produces(MediaType.APPLICATION_JSON)
  @Transactional
  public List<CohortSetOutcomeItem> getCohortSetOutcomes(
          @PathParam("studyId") final int studyId,
					@PathParam("cohortSetId") final int cohortSetId
  ) {
		String translatedSql;

		// Retrieve the cohort set 
    CohortSet cs = cohortSetRepository.findOne(cohortSetId);
    if (cs == null) {
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }
		
		// Get the cohorts tied to this cohort set
		ArrayList<String> cohortIdList = new ArrayList<String>();
		cs.getCohortList().forEach(item -> {
			cohortIdList.add(item.getId().toString());
		});
		
		String cohortListEquality = "";
		if (cohortIdList.size() > 1) {
			cohortListEquality = String.format("in (%s)", StringUtils.join(cohortIdList, ","));			
		} else if (cohortIdList.size() == 1) {
			cohortListEquality = "= " + cohortIdList.get(0);			
		} else {
			throw new WebApplicationException(Response.Status.NOT_FOUND);
		}
      
    String getDashboardQuery = SqlRender.renderSql(
            QUERY_DASHBOARD_OUTCOMES, 
            new String[] {"study_results_schema", "cohort_list_equality", "study_id"},
            new String[] {this.getStudyResultsSchema(), cohortListEquality, Integer.toString(studyId)}
    );
		
    translatedSql = SqlTranslate.translateSql(getDashboardQuery, "sql server", this.getStudyResultsDialect());
    List<CohortSetOutcomeItem> returnVal =  this.getStudyResultsJdbcTemplate().query(translatedSql, (rs,rowNum) -> { 
      CohortSetOutcomeItem mappedRow = new CohortSetOutcomeItem() {{
        outcomeCohortId = rs.getLong("outcome_cohort_definition_id");
        outcomeCohortName = rs.getString("outcome_cohort_name");
        outcomeConceptId = rs.getLong("outcome_concept_id");
      }};
      return mappedRow;
    });    
    
    return returnVal;
  }
	

  @GET
  @Path("/{studyId}")
  @Produces(MediaType.APPLICATION_JSON)
  @Transactional
  public StudyDTO getStudy(
          @PathParam("studyId") final int studyId
  ) {
    Study studyEntity = studyRepository.findOne(studyId);
    if (studyEntity == null) {
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }
      
    // resolve entity collections into POJO collections for JSON serialization.
    // later we should adopt a DTO mapper when we implement services to update a Study.
    
    return fromStudy(studyEntity);
  }

	@GET
  @Path("/{studyId}/dashboard/{cohortSetId}/explore/{conceptId}")
  @Produces(MediaType.APPLICATION_JSON)
  @Transactional
  public List<DashboardItem> getDashboardByVocab(
          @PathParam("studyId") final int studyId,
					@PathParam("cohortSetId") final int cohortSetId,
					@PathParam("conceptId") final int conceptId
  ) {
		String translatedSql;

		// Retrieve the cohort set 
    CohortSet cs = cohortSetRepository.findOne(cohortSetId);
    if (cs == null) {
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }
		
		// Get the cohorts tied to this cohort set
		ArrayList<String> cohortIdList = new ArrayList<String>();
		cs.getCohortList().forEach(item -> {
			cohortIdList.add(item.getId().toString());
		});
		
		String cohortListEquality = "";
		if (cohortIdList.size() > 1) {
			cohortListEquality = String.format("in (%s)", StringUtils.join(cohortIdList, ","));			
		} else if (cohortIdList.size() == 1) {
			cohortListEquality = "= " + cohortIdList.get(0);			
		} else {
			throw new WebApplicationException(Response.Status.NOT_FOUND);
		}
      
    String getDashboardByConceptQuery = SqlRender.renderSql(QUERY_DASHBOARD_VOCAB, 
            new String[] {"study_results_schema", "cohort_list_equality", "study_id", "outcome_concept_id"},
            new String[] {this.getStudyResultsSchema(), cohortListEquality, Integer.toString(studyId), Integer.toString(conceptId)}
    );
		
    translatedSql = SqlTranslate.translateSql(getDashboardByConceptQuery, "sql server", this.getStudyResultsDialect());
    List<DashboardItem> dashboard =  this.getStudyResultsJdbcTemplate().query(translatedSql, (rs,rowNum) -> { 
      DashboardItem mappedRow = new DashboardItem() {{
        targetCohortId = rs.getLong("target_cohort_definition_id");
        targetCohortName = rs.getString("target_cohort_name");
        outcomeCohortId = rs.getLong("outcome_cohort_definition_id");
        outcomeCohortName = rs.getString("outcome_cohort_name");
        outcomeConceptId = rs.getLong("outcome_concept_id");
        outcomeConceptName = rs.getString("concept_name");
        incidence = new BigDecimal(rs.getDouble("incidence")).setScale(5, RoundingMode.DOWN);
        estimate = new BigDecimal(rs.getDouble("estimate")).setScale(5, RoundingMode.DOWN);
        negativeControl = rs.getInt("nc");
				onLabel = rs.getInt("on_label");
				distance = rs.getInt("min_levels_of_separation");
      }};
      return mappedRow;
    });    
		
    
    return dashboard;
  }
	
	@GET
  @Path("/{studyId}/dashboard/{cohortSetId}")
  @Produces(MediaType.APPLICATION_JSON)
  @Transactional
  public List<DashboardItem> getDashboard(
          @PathParam("studyId") final int studyId,
					@PathParam("cohortSetId") final int cohortSetId
  ) {
		String translatedSql;

		// Retrieve the cohort set 
    CohortSet cs = cohortSetRepository.findOne(cohortSetId);
    if (cs == null) {
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }
		
		// Get the cohorts tied to this cohort set
		ArrayList<String> cohortIdList = new ArrayList<String>();
		cs.getCohortList().forEach(item -> {
			cohortIdList.add(item.getId().toString());
		});
		
		String cohortListEquality = "";
		if (cohortIdList.size() > 1) {
			cohortListEquality = String.format("in (%s)", StringUtils.join(cohortIdList, ","));			
		} else if (cohortIdList.size() == 1) {
			cohortListEquality = "= " + cohortIdList.get(0);			
		} else {
			throw new WebApplicationException(Response.Status.NOT_FOUND);
		}
      
    String getDashboardQuery = SqlRender.renderSql(
            QUERY_DASHBOARD, 
            new String[] {"study_results_schema", "cohort_list_equality", "study_id"},
            new String[] {this.getStudyResultsSchema(), cohortListEquality, Integer.toString(studyId)}
    );
		
    translatedSql = SqlTranslate.translateSql(getDashboardQuery, "sql server", this.getStudyResultsDialect());
    List<DashboardItem> dashboard =  this.getStudyResultsJdbcTemplate().query(translatedSql, (rs,rowNum) -> { 
      DashboardItem mappedRow = new DashboardItem() {{
        targetCohortId = rs.getLong("target_cohort_definition_id");
        targetCohortName = rs.getString("target_cohort_name");
        outcomeCohortId = rs.getLong("outcome_cohort_definition_id");
        outcomeCohortName = rs.getString("outcome_cohort_name");
        outcomeConceptId = rs.getLong("outcome_concept_id");
        outcomeConceptName = rs.getString("concept_name");
        incidence = new BigDecimal(rs.getDouble("incidence")).setScale(5, RoundingMode.DOWN);
        estimate = new BigDecimal(rs.getDouble("estimate")).setScale(5, RoundingMode.DOWN);
				onLabel = rs.getInt("on_label");
        negativeControl = rs.getInt("nc");
      }};
      return mappedRow;
    });    
		
    
    return dashboard;
  }
}