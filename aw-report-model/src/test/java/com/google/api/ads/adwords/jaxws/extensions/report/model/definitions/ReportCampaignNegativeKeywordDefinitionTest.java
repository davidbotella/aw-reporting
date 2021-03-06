package com.google.api.ads.adwords.jaxws.extensions.report.model.definitions;

import com.google.api.ads.adwords.jaxws.extensions.report.model.entities.ReportCampaignNegativeKeyword;
import com.google.api.ads.adwords.lib.jaxb.v201309.ReportDefinitionReportType;

import junit.framework.Assert;

import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Tests the Negative Keywords Performance report definition.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:aw-report-model-test-beans.xml")
public class ReportCampaignNegativeKeywordDefinitionTest
    extends AbstractReportDefinitionTest<ReportCampaignNegativeKeyword> {

  /**
   * C'tor
   */
  public ReportCampaignNegativeKeywordDefinitionTest() {

    super(ReportCampaignNegativeKeyword.class,
        ReportDefinitionReportType.CAMPAIGN_NEGATIVE_KEYWORDS_PERFORMANCE_REPORT,
        "src/test/resources/csv/campaign-negative.csv");
  }

  /**
   * @see com.google.api.ads.adwords.jaxws.extensions.report.model.definitions.AbstractReportDefinitionTest
   *      #testFirstEntry(com.google.api.ads.adwords.jaxws.extensions.report.model.entities.Report)
   */
  @Override
  protected void testFirstEntry(ReportCampaignNegativeKeyword first) {

    Assert.assertEquals(11533780L, first.getKeywordId().longValue());
    Assert.assertEquals(116981433L, first.getCampaignId().longValue());
    Assert.assertEquals("Broad", first.getKeywordMatchType());
    Assert.assertEquals("gratuite", first.getKeywordText());
    Assert.assertTrue(first.isNegative());

  }

  /**
   * @see com.google.api.ads.adwords.jaxws.extensions.report.model.definitions.AbstractReportDefinitionTest
   *      #testLastEntry(com.google.api.ads.adwords.jaxws.extensions.report.model.entities.Report)
   */
  @Override
  protected void testLastEntry(ReportCampaignNegativeKeyword last) {

    Assert.assertEquals(11679830L, last.getKeywordId().longValue());
    Assert.assertEquals(116996313L, last.getCampaignId().longValue());
    Assert.assertEquals("Broad", last.getKeywordMatchType());
    Assert.assertEquals("gratuit", last.getKeywordText());
    Assert.assertTrue(last.isNegative());

  }

  /**
   * @see com.google.api.ads.adwords.jaxws.extensions.report.model.definitions.AbstractReportDefinitionTest
   *      #retrieveCsvEntries()
   */
  @Override
  protected int retrieveCsvEntries() {

    return 8;
  }

  /**
   * @see com.google.api.ads.adwords.jaxws.extensions.report.model.definitions.AbstractReportDefinitionTest
   *      #retrievePropertiesToBeSelected()
   */
  @Override
  protected String[] retrievePropertiesToBeSelected() {
    return new String[] {"CampaignId", "Id", "KeywordMatchType", "KeywordText", "IsNegative"};
  }
}