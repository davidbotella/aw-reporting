// Copyright 2012 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.api.ads.adwords.jaxws.extensions;

import com.google.api.ads.adwords.jaxws.extensions.processors.ReportProcessor;
import com.google.api.ads.adwords.jaxws.extensions.proxy.JaxWsProxySelector;
import com.google.api.ads.adwords.jaxws.extensions.report.model.entities.Report;
import com.google.api.ads.adwords.jaxws.extensions.report.model.entities.ReportAccount;
import com.google.api.ads.adwords.jaxws.extensions.report.model.persistence.EntityPersister;
import com.google.api.ads.adwords.jaxws.extensions.report.model.util.DateUtil;
import com.google.api.ads.adwords.jaxws.extensions.util.DataBaseType;
import com.google.api.ads.adwords.jaxws.extensions.util.DynamicPropertyPlaceholderConfigurer;
import com.google.api.ads.adwords.jaxws.extensions.util.FileUtil;
import com.google.api.ads.adwords.jaxws.extensions.util.HTMLExporter;
import com.google.api.ads.adwords.lib.jaxb.v201309.ReportDefinitionDateRangeType;
import com.google.api.ads.adwords.lib.jaxb.v201309.ReportDefinitionReportType;
import com.google.api.client.util.Sets;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ProxySelector;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;

/**
 * Main class that executes the report processing logic delegating to the {@link ReportProcessor}.
 *
 *  This class holds a Spring application context that manages the creation of all the beans needed.
 * No configuration is done in this class.
 *
 *  Credentials and properties are pulled from the ~/aw-report-sample.properties.properties file or
 * -file <file> provided.
 *
 * See README for more info.
 *
 * @author jtoledo@google.com (Julian Toledo)
 * @author gustavomoreira@google.com (Gustavo Moreira)
 */
public class AwReporting {

  private static final Logger LOGGER = Logger.getLogger(AwReporting.class);

  /**
   * The DB type key specified in the properties file.
   */
  private static final String AW_REPORT_MODEL_DB_TYPE = "aw.report.model.db.type";

  /**
   * The Spring application context used to get all the beans.
   */
  private static ApplicationContext appCtx;

  /**
   * Main method.
   *
   * @param args the command line arguments.
   */
  public static void main(String args[]) {

    // Proxy
    JaxWsProxySelector ps = new JaxWsProxySelector(ProxySelector.getDefault());
    ProxySelector.setDefault(ps);

    Options options = createCommandLineOptions();

    boolean errors = false;
    String propertiesPath = null;

    try {
      CommandLineParser parser = new BasicParser();
      CommandLine cmdLine = parser.parse(options, args);

      // Print full help and quit
      if (cmdLine.hasOption("help")) {
        printHelpMessage(options);
        printSamplePropertiesFile();
        System.exit(0);
      }

      setLogLevel(cmdLine);

      if (cmdLine.hasOption("file")) {
        propertiesPath = cmdLine.getOptionValue("file");
      }
      LOGGER.info("Using properties file: " + propertiesPath);

      Set<Long> accountIdsSet = Sets.newHashSet();
      if (cmdLine.hasOption("accountIdsFile")) {
        String accountsFileName = cmdLine.getOptionValue("accountIdsFile");
        addAccountsFromFile(accountIdsSet, accountsFileName);
      }

      Properties properties = initApplicationContextAndProperties(propertiesPath);

      LOGGER.debug("Creating ReportProcessor bean...");
      ReportProcessor processor = createReportProcessor();
      LOGGER.debug("... success.");

      if (cmdLine.hasOption("generatePdf")) {

        LOGGER.debug("GeneratePDF option detected.");

        // Get HTML template and output directoy
        String[] pdfFiles = cmdLine.getOptionValues("generatePdf");
        File htmlTemplateFile = new File(pdfFiles[0]);
        File outputDirectory = new File(pdfFiles[1]);

        LOGGER.debug("Html template file to be used: " + htmlTemplateFile);
        LOGGER.debug("Output directory for PDF: " + outputDirectory);

        // Generate PDFs
        generatePdf(processor,
            cmdLine.getOptionValue("startDate"),
            cmdLine.getOptionValue("endDate"),
            properties,
            htmlTemplateFile,
            outputDirectory);

      } else if (cmdLine.hasOption("startDate") && cmdLine.hasOption("endDate")) {
        // Generate Reports

        String dateStart = cmdLine.getOptionValue("startDate");
        String dateEnd = cmdLine.getOptionValue("endDate");

        LOGGER.info(
            "Starting report download for dateStart: " + dateStart + " and dateEnd: " + dateEnd);

        processor.generateReportsForMCC(
            ReportDefinitionDateRangeType.CUSTOM_DATE, dateStart, dateEnd, null, properties);

      } else if (cmdLine.hasOption("dateRange")) {

        ReportDefinitionDateRangeType dateRangeType =
            ReportDefinitionDateRangeType.fromValue(cmdLine.getOptionValue("dateRange"));

        LOGGER.info("Starting report download for dateRange: " + dateRangeType.name());

        processor.generateReportsForMCC(dateRangeType, null, null, null, properties);

      } else {
        errors = true;
        LOGGER.error("Configuration incomplete. Missing options for command line.");
      }
    } catch (IOException e) {
      errors = true;
      LOGGER.error("File not found: " + e.getMessage());
    } catch (ParseException e) {
      errors = true;
      LOGGER.error("Error parsing the values for the command line options: " + e.getMessage());
    } catch (Exception e) {
      errors = true;
      LOGGER.error("Unexpected error accessing the API: " + e.getMessage());
      e.printStackTrace();
    }

    if (errors) {
      System.exit(1);
    } else {
      System.exit(0);
    }
  }

  /**
   * Reads the account ids from the file, and adds them to the given set.
   *
   * @param accountIdsSet the set to add the accounts
   * @param accountsFileName the file to be read
   * @throws FileNotFoundException file not found
   */
  protected static void addAccountsFromFile(Set<Long> accountIdsSet, String accountsFileName)
      throws FileNotFoundException {

    LOGGER.info("Using accounts file: " + accountsFileName);

    List<String> linesAsStrings = FileUtil.readFileLinesAsStrings(new File(accountsFileName));

    LOGGER.debug("Acount IDs to be queried:");
    for (String line : linesAsStrings) {

      String accountIdAsString = line.replaceAll("-", "");
      long accountId = Long.parseLong(accountIdAsString);
      accountIdsSet.add(accountId);

      LOGGER.debug("Acount ID: " + accountId);
    }
  }

  /**
   * Creates the {@link ReportProcessor} autowiring all the dependencies.
   *
   * @return the {@code ReportProcessor} with all the dependencies properly injected.
   */
  private static ReportProcessor createReportProcessor() {

    return appCtx.getBean(ReportProcessor.class);
  }

  /**
   * Creates the command line options.
   *
   * @return the {@link Options}.
   */
  private static Options createCommandLineOptions() {

    Options options = new Options();
    Option help = new Option("help", "print this message");
    options.addOption(help);

    OptionBuilder.withArgName("file");
    OptionBuilder.hasArg(true);
    OptionBuilder.withDescription("aw-report-sample.properties file.");
    OptionBuilder.isRequired(true);
    options.addOption(OptionBuilder.create("file"));

    OptionBuilder.withArgName("YYYYMMDD");
    OptionBuilder.hasArg(true);
    OptionBuilder.withDescription("Start date for CUSTOM_DATE Reports (YYYYMMDD)");
    OptionBuilder.isRequired(false);
    options.addOption(OptionBuilder.create("startDate"));

    OptionBuilder.withArgName("YYYMMDD");
    OptionBuilder.hasArg(true);
    OptionBuilder.withDescription("End date for CUSTOM_DATE Reports (YYYYMMDD)");
    OptionBuilder.isRequired(false);
    options.addOption(OptionBuilder.create("endDate"));

    OptionBuilder.withArgName("DateRangeType");
    OptionBuilder.hasArg(true);
    OptionBuilder.withDescription("ReportDefinitionDateRangeType");
    OptionBuilder.isRequired(false);
    options.addOption(OptionBuilder.create("dateRange"));

    OptionBuilder.withArgName("htmlTemplateFile> <outputDirectory");
    OptionBuilder.withValueSeparator(' ');
    OptionBuilder.hasArgs(2);
    OptionBuilder.withDescription("Generate Monthly Account Reports for all Accounts in PDF\n"
        + "NOTE: For PDF use aw-report-sample-for-pdf.properties instead, the fields need to be different.");
    options.addOption(OptionBuilder.create("generatePdf"));

    OptionBuilder.withArgName("accountIdsFile");
    OptionBuilder.hasArg(false);
    OptionBuilder.withDescription(
        "Consider ONLY the account IDs specified on the file to run the report");
    OptionBuilder.isRequired(false);
    options.addOption(OptionBuilder.create("accountIdsFile"));

    OptionBuilder.withArgName("verbose");
    OptionBuilder.hasArg(false);
    OptionBuilder.withDescription("The application will print all the tracing on the console");
    OptionBuilder.isRequired(false);
    options.addOption(OptionBuilder.create("verbose"));

    OptionBuilder.withArgName("debug");
    OptionBuilder.hasArg(false);
    OptionBuilder.withDescription("Will display all the debug information. "
        + "If the option 'verbose' is activated, "
        + "all the information will be displayed on the console as well");
    OptionBuilder.isRequired(false);
    options.addOption(OptionBuilder.create("debug"));

    return options;
  }

  /**
   * Prints the help message.
   *
   * @param options the options available for the user.
   */
  private static void printHelpMessage(Options options) {

    // automatically generate the help statement
    System.out.println();
    HelpFormatter formatter = new HelpFormatter();
    formatter.setWidth(120);
    formatter
        .printHelp(
            " java -Xmx1G -jar aw-reporting.jar -startDate YYYYMMDD -endDate YYYYMMDD "
                + "-file <file>\n java -Xmx1G -jar aw-reporting.jar "
                + "-generatePdf <htmlTemplateFile> <outputDirectory> -startDate YYYYMMDD -endDate YYYYMMDD -file <file>",
            "\nArguments:", options, "");
    System.out.println();
  }

  /**
   * Prints the sample properties file on the default output.
   */
  private static void printSamplePropertiesFile() {

    System.out.println("\n  File: aw-report-sample.properties example");

    ClassPathResource sampleFile = new ClassPathResource("aw-report-sample.properties");
    Scanner fileScanner = null;
    try {
      fileScanner = new Scanner(sampleFile.getInputStream());
      while (fileScanner.hasNext()) {
        System.out.println(fileScanner.nextLine());
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (fileScanner != null) {
        fileScanner.close();
      }
    }
  }

  /**
   * Sets the Log level based on the command line arguments
   *
   * @param commandLine the command line
   */
  private static void setLogLevel(CommandLine commandLine) {

    Level logLevel = Level.INFO;

    if (commandLine.hasOption("debug")) {
      logLevel = Level.DEBUG;
    }

    ConsoleAppender console = new ConsoleAppender(); // create appender
    String PATTERN = "%d [%p|%c|%C{1}] %m%n";
    console.setLayout(new PatternLayout(PATTERN));
    console.activateOptions();
    if (commandLine.hasOption("verbose")) {
      console.setThreshold(logLevel);
    } else {
      console.setThreshold(Level.ERROR);
    }
    Logger.getLogger("com.google.api.ads.adwords.jaxws.extensions").addAppender(console);

    FileAppender fa = new FileAppender();
    fa.setName("FileLogger");
    fa.setFile("aw-reporting.log");
    fa.setLayout(new PatternLayout("%d %-5p [%c{1}] %m%n"));
    fa.setThreshold(logLevel);
    fa.setAppend(true);
    fa.activateOptions();
    Logger.getLogger("com.google.api.ads.adwords.jaxws.extensions").addAppender(fa);

  }

  /**
   * Initialize the application context, adding the properties configuration file depending on the
   * specified path.
   *
   * @param propertiesPath the path to the file.
   * @return the resource loaded from the properties file.
   * @throws IOException error opening the properties file.
   */
  private static Properties initApplicationContextAndProperties(String propertiesPath)
      throws IOException {

    Resource resource = new ClassPathResource(propertiesPath);
    if (!resource.exists()) {
      resource = new FileSystemResource(propertiesPath);
    }
    LOGGER.trace("Innitializing Spring application context.");
    DynamicPropertyPlaceholderConfigurer.setDynamicResource(resource);

    Properties properties = PropertiesLoaderUtils.loadProperties(resource);
    String dbType = (String) properties.get(AW_REPORT_MODEL_DB_TYPE);
    if (dbType != null && dbType.equals(DataBaseType.MONGODB.name())) {

      LOGGER.debug("Using MONGO DB configuration properties.");
      appCtx = new ClassPathXmlApplicationContext("classpath:aw-reporting-mongodb-beans.xml");
    } else {

      LOGGER.debug("Using SQL DB configuration properties.");
      appCtx = new ClassPathXmlApplicationContext("classpath:aw-reporting-sql-beans.xml");
    }

    return properties;
  }

  /**
   * Generates the PDF files from the report data
   *
   * @param processor the report processo
   * @param dateStart the start date for the reports
   * @param dateEnd the end date for the reports
   * @param properties the properties file containing all the configuration
   * @throws Exception error creating PDF
   */
  private static void generatePdf(ReportProcessor processor,
      String dateStart,
      String dateEnd,
      Properties properties,
      File htmlTemplateFile,
      File outputDirectory) throws Exception {

    EntityPersister entityPersister = appCtx.getBean(EntityPersister.class);

    LOGGER.info("Starting PDF Generation");

    for (Long accountId : processor.retrieveAccountIds()) {

      LOGGER.info(".");
      LOGGER.debug("Retrieving monthly reports for account: " + accountId);

      List<? extends Report> montlyAccountReports = entityPersister.listMonthReports(
          ReportAccount.class, accountId, DateUtil.parseDateTime(dateStart),
          DateUtil.parseDateTime(dateEnd));

      if (montlyAccountReports != null && montlyAccountReports.size() > 0) {

        File htmlFile = new File(outputDirectory, "ReportAccount" + accountId + ".html");
        File pdfFile = new File(outputDirectory, "ReportAccount" + accountId + ".pdf");

        LOGGER.debug("Exporting monthly reports to HTML for account: " + accountId);
        HTMLExporter.exportHTML("Test", ReportDefinitionReportType.ACCOUNT_PERFORMANCE_REPORT,
            montlyAccountReports, htmlTemplateFile, htmlFile);

        LOGGER.debug("Converting HTML to PDF for account: " + accountId);
        HTMLExporter.convertHTMLtoPDF(htmlFile, pdfFile);
      }
    }
  }
}
