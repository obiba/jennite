/*
 * Copyright (c) 2017 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.jennite.vcf;

import org.obiba.core.util.FileUtil;
import org.obiba.opal.spi.vcf.VCFStore;
import org.obiba.opal.spi.vcf.VCFStoreException;
import org.obiba.opal.spi.vcf.VCFStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

/**
 * All VCF files of the store are in a dedicated directory.
 */
public class JenniteVCFStore implements VCFStore {

  private static final Logger log = LoggerFactory.getLogger(JenniteVCFStore.class);

  private static final SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyyMMdd_HHmmss");

  private static final String VCF_FILE = "data.vcf";

  private static final String BCF_FILE = "data.bcf";

  private static final String VCF_GZ_FILE = VCF_FILE + ".gz";

  private static final String BCF_GZ_FILE = BCF_FILE + ".gz";

  private static final String VCF_GZ_INDEX = VCF_GZ_FILE + ".tbi";

  private static final String BCF_GZ_INDEX = BCF_GZ_FILE + ".csi";

  private static final String SAMPLES_FILE = "samples.txt";

  private static final String STATS_FILE = "statistics.tsv";

  private static final String VCF_PROPERTIES_FILE = "vcf.properties";

  private static final String EXEC_LOG = "exec.log";

  private final String name;

  private final Properties properties;

  public JenniteVCFStore(String name, Properties properties) {
    this.name = name;
    this.properties = properties;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Collection<String> getSampleIds() {
    Set<String> sampleIds = new LinkedHashSet<>();
    getVCFNames().forEach(name -> {
      File samplesFile = getSamplesFile(name);
      if (samplesFile.exists()) {
        try (Stream<String> stream = Files.lines(samplesFile.toPath())) {
          stream.forEach(line -> sampleIds.add(line));
        } catch (IOException e) {
          log.warn("Failure when reading samples list: " + samplesFile.getAbsolutePath(), e);
        }
      }
    });
    return sampleIds;
  }

  @Override
  public Collection<String> getVCFNames() {
    List<String> names = new ArrayList<>();
    File directory = new File(properties.getProperty("data.dir"), name);
    File[] children = directory.listFiles(File::isDirectory);
    if (children == null) return names;
    for (File child : children) {
      // a new VCF could be in treatment, so report only the ones with properties ready
      File propFile = getVCFPropertiesFile(child.getName());
      if (propFile.exists()) names.add(child.getName());
    }
    return names;
  }

  @Override
  public boolean hasVCF(String vcfName) {
    return getVCFGZFile(vcfName).exists();
  }

  @Override
  public VCFSummary getVCFSummary(String vcfName) throws NoSuchElementException {
    if (!hasVCF(vcfName)) throw new NoSuchElementException("No VCF with name '" + vcfName + "' can be found");
    return JenniteVCFSummary.newSummary(vcfName).properties(getVCFPropertiesFile(vcfName)).samples(getSamplesFile(vcfName)).build();
  }

  /**
   * Prepare VCF folder and write VCF/BCF file, compress it if necessary and index it (requires VCF/BCF to be sorted).
   *
   * @param vcfName
   * @param vcf
   * @throws IOException
   */
  @Override
  public void writeVCF(String vcfName, InputStream vcf) throws IOException {
    // writing a VCF is making a directory with the compressed and indexed VCF file
    String store;
    Format format = Format.VCF;
    boolean isCompressed = false;
    if (vcfName.endsWith(".vcf")) {
      store = vcfName.replaceAll("\\.vcf$", "");
    }
    else if (vcfName.endsWith(".vcf.gz")) {
      store = vcfName.replaceAll("\\.vcf.gz$", "");
      isCompressed = true;
    }
    else if (vcfName.endsWith(".bcf")) {
      store = vcfName.replaceAll("\\.bcf$", "");
      format = Format.BCF;
    }
    else if (vcfName.endsWith(".bcf.gz")) {
      store = vcfName.replaceAll("\\.bcf.gz$", "");
      isCompressed = true;
      format = Format.BCF;
    }
    else {
      // assume it is a compressed VCF file
      store = vcfName;
      isCompressed = true;
    }

    // write data file, replacing anything that could be found at the VCF folder location
    File vcfFolder = getVCFFolder(store);
    if (vcfFolder.exists()) FileUtil.delete(vcfFolder);
    vcfFolder.mkdirs();
    File destination = isCompressed ? getVCFGZFile(store, format) : new File(getVCFFolder(store), format.equals(Format.VCF) ? VCF_FILE : BCF_FILE);
    Files.copy(vcf, destination.toPath());

    if (!isCompressed) compress(store, format);
    index(store);
    listSamples(store);
    statistics(store);
    properties(store, vcfName);
  }

  @Override
  public void deleteVCF(String vcfName) {
    File store = getVCFFolder(vcfName);
    if (!store.exists()) return;
    try {
      FileUtil.delete(store);
    } catch (IOException e) {
      // ignore
    }
  }

  @Override
  public void readVCF(String vcfName, OutputStream out) throws NoSuchElementException, IOException {
    readVCF(vcfName, getFormat(vcfName), out);
  }

  @Override
  public void readVCF(String vcfName, Format format, OutputStream out) throws NoSuchElementException, IOException {
    if (!hasVCF(vcfName)) throw new NoSuchElementException("No VCF with name '" + vcfName + "' can be found");
    if (getFormat(vcfName) == format)
      Files.copy(getVCFGZFile(vcfName).toPath(), out);
    else {
      // need to convert VCF format flavour
      String timestamp = dateTimeFormatter.format(System.currentTimeMillis());
      File workDir = getVCFWorkFolder(vcfName);
      File outputFile = new File(workDir, "data_" + timestamp + "." + format.name().toLowerCase() + ".gz");
      outputFile.deleteOnExit();

      String outputType = Format.VCF == format ? "z" : "b";
      int status = runProcess(vcfName, bcftools("view",
          "--output-type", outputType, // compressed VCF/BCF
          "--output-file", outputFile.getAbsolutePath()));
      if (status != 0) throw new VCFStoreException("VCF/BCF file format conversion using bcftools failed.");

      Files.copy(outputFile.toPath(), out);
    }
  }

  @Override
  public void readVCF(String vcfName, OutputStream out, Collection<String> samples) throws NoSuchElementException, IOException {
    readVCF(vcfName, getFormat(vcfName), out, samples);
  }

  @Override
  public void readVCF(String vcfName, Format format, OutputStream out, Collection<String> samples) throws NoSuchElementException, IOException {
    if (!hasVCF(vcfName)) throw new NoSuchElementException("No VCF with name '" + vcfName + "' can be found");
    String timestamp = dateTimeFormatter.format(System.currentTimeMillis());
    File workDir = getVCFWorkFolder(vcfName);
    File samplesFile = new File(workDir, "samples_" + timestamp + ".txt");
    File outputFile = new File(workDir, "data_" + timestamp + "." + format.name().toLowerCase() + ".gz");
    outputFile.deleteOnExit();
    samplesFile.deleteOnExit();

    try (BufferedWriter writer = Files.newBufferedWriter(samplesFile.toPath())) {
      for (String s : samples) {
        writer.write(s);
        writer.newLine();
      }
    }

    String outputType = Format.VCF == format ? "z" : "b";
    int status = runProcess(vcfName, bcftools("view",
        "--samples-file", samplesFile.getAbsolutePath(),
        "--output-type", outputType, // compressed VCF/BCF
        "--output-file", outputFile.getAbsolutePath()));
    if (status != 0) throw new VCFStoreException("VCF/BCF file subset by samples using bcftools failed.");

    Files.copy(outputFile.toPath(), out);
  }

  @Override
  public void readVCFStatistics(String vcfName, OutputStream out) throws NoSuchElementException, IOException {
    if (!hasVCF(vcfName)) throw new NoSuchElementException("No VCF with name '" + vcfName + "' can be found");
    Files.copy(getStatsFile(vcfName).toPath(), out);
  }

  //
  // Private methods
  //

  private void compress(String vcfName, Format format) {
    File dataFile = new File(getVCFFolder(vcfName), Format.VCF == format ? VCF_FILE : BCF_FILE);
    if (!dataFile.exists()) return;
    int status = runProcess(vcfName, bgzip("-f", dataFile.getAbsolutePath()));
    if (status != 0) throw new VCFStoreException("VCF/BCF file compression using bgzip failed");
  }

  private void index(String vcfName) {
    int status = runProcess(vcfName, tabix("-f", "-p", getFormat(vcfName).name().toLowerCase(), getVCFGZFile(vcfName).getAbsolutePath()));
    if (status != 0) throw new VCFStoreException("VCF/BCF file indexing using tabix failed");
  }

  private void listSamples(String vcfName) {
    int status = runProcess(vcfName, bcftools("query", "--list-samples", getVCFGZFile(vcfName).getAbsolutePath()),
        ProcessBuilder.Redirect.to(getSamplesFile(vcfName)));
    if (status != 0) throw new VCFStoreException("VCF/BCF file samples listing using bcftools failed");
  }

  private void statistics(String vcfName) {
    int status = runProcess(vcfName, bcftools("stats", getVCFGZFile(vcfName).getAbsolutePath()),
        ProcessBuilder.Redirect.to(getStatsFile(vcfName)));
    if (status != 0) throw new VCFStoreException("VCF/BCF file statistics extraction using bcftools failed");
  }

  private void properties(String vcfName, String originalVcfName) {
    try (OutputStream out = new FileOutputStream(getVCFPropertiesFile(vcfName))) {
      Properties prop = new Properties();
      // track version of vcf store service
      prop.setProperty("name", vcfName);
      prop.setProperty("name.original", originalVcfName);
      prop.setProperty("version", properties.getProperty("version"));
      VCFSummary summary = JenniteVCFSummary.newSummary(vcfName).format(getFormat(vcfName))
          .size(getVCFGZFile(vcfName)).samples(getSamplesFile(vcfName))
          .statistics(getStatsFile(vcfName)).build();
      prop.setProperty("summary.format", summary.getFormat().name());
      prop.setProperty("summary.genotypes.count", "" + summary.getGenotypesCount());
      prop.setProperty("summary.variants.count", "" + summary.getVariantsCount());
      prop.setProperty("summary.size", "" + summary.size());
      prop.setProperty("summary.samples.count", "" + summary.getSampleIds().size());
      prop.store(out, null);
    } catch (Exception e)  {
      // ignore
    }
  }

  /**
   * Get the VCF data folder location.
   *
   * @param vcfName
   * @return
   */
  private File getVCFFolder(String vcfName) {
    return new File(properties.getProperty(VCFStoreService.DATA_DIR_PROPERTY), name + File.separator + vcfName);
  }

  /**
   * Get the VCF work folder location.
   *
   * @param vcfName
   * @return
   */
  private File getVCFWorkFolder(String vcfName) {
    File workDir = new File(properties.getProperty(VCFStoreService.WORK_DIR_PROPERTY), name + File.separator + vcfName);
    if (!workDir.exists()) workDir.mkdirs();
    return workDir;
  }

  /**
   * Get VCF/BCF compressed file location by specifying the format.
   *
   * @param vcfName
   * @param format
   * @return
   */
  private File getVCFGZFile(String vcfName, Format format) {
    return Format.VCF == format ?
        new File(getVCFFolder(vcfName), VCF_GZ_FILE) :
        new File(getVCFFolder(vcfName), BCF_GZ_FILE);
  }

  /**
   * Get VCF/BCF compressed file location.
   *
   * @param vcfName
   * @return
   */
  private File getVCFGZFile(String vcfName) {
    File dataFile = new File(getVCFFolder(vcfName), VCF_GZ_FILE);
    return dataFile.exists() ? dataFile : new File(getVCFFolder(vcfName), BCF_GZ_FILE);
  }

  /**
   * Get the VCF file format flavour.
   *
   * @param vcfName
   * @return
   */
  private Format getFormat(String vcfName) {
    return getVCFGZFile(vcfName).getName().equals(VCF_GZ_FILE) ? Format.VCF : Format.BCF;
  }

  /**
   * Get samples file location.
   *
   * @param vcfName
   * @return
   */
  private File getSamplesFile(String vcfName) {
    return new File(getVCFFolder(vcfName), SAMPLES_FILE);
  }

  /**
   * Get statistics file location.
   *
   * @param vcfName
   * @return
   */
  private File getStatsFile(String vcfName) {
    return new File(getVCFFolder(vcfName), STATS_FILE);
  }

  /**
   * Get the VCF file properties location.
   *
   * @param vcfName
   * @return
   */
  private File getVCFPropertiesFile(String vcfName) {
    return new File(getVCFFolder(vcfName), VCF_PROPERTIES_FILE);
  }

  private int runProcess(String vcfName, String[] command) {
    return runProcess(vcfName, command, null);
  }

  private int runProcess(String vcfName, String[] command, ProcessBuilder.Redirect redirect) {
    int rval = -1;
    try {
      ProcessBuilder.Redirect red = redirect == null ? ProcessBuilder.Redirect.appendTo(new File(getVCFFolder(vcfName), EXEC_LOG)) : redirect;
      Process process = buildProcess(vcfName, command, red).start();
      rval = process.waitFor();
    } catch (Exception e) {
      log.error("Process execution failed", e);
    }
    log.info("{} >> {}", String.join(" ", command), rval);
    return rval;
  }

  /**
   * Build a process that will be executed in the VCF folder.
   *
   * @param vcfName
   * @param command
   * @return
   */
  private ProcessBuilder buildProcess(String vcfName, String[] command, ProcessBuilder.Redirect redirect) {
    File store = getVCFFolder(vcfName);
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(store);
    pb.redirectErrorStream(true);
    pb.redirectOutput(redirect);
    return pb;
  }

  /**
   * Get bcftools command, for data extraction (samples, statistics, subset).
   *
   * @param args
   * @return
   */
  private String[] bcftools(String... args) {
    return getCommand("bcftools", args);
  }

  /**
   * Get bgzip command, for compression.
   *
   * @param args
   * @return
   */
  private String[] bgzip(String... args) {
    return getCommand("bgzip", args);
  }

  /**
   * Get tabix command, for indexing.
   *
   * @param args
   * @return
   */
  private String[] tabix(String... args) {
    return getCommand("tabix", args);
  }

  private String[] getCommand(String name, String... args) {
    String[] command = new String[args.length + 1];
    command[0] = getExec(name);
    for (int i=0; i<args.length; i++) {
      command[i+1] = args[i];
    }
    return command;
  }

  private String getExec(String name) {
    return properties.getProperty("exec." + name, "/usr/local/bin/" + name);
  }
}
