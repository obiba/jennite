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

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * All VCF files of the store are in a dedicated directory.
 */
public class JenniteVCFStore implements VCFStore {

  private static final String VCF_FILE = "data.vcf";

  private static final String VCF_GZ_FILE = "data.vcf.gz";

  private static final String VCF_GZ_INDEX = VCF_GZ_FILE + ".tbi";

  private static final String SAMPLES_FILE = "samples.txt";

  private static final String EXEC_LOG = "exec.log";

  private final String name;

  private final File directory;

  private final Properties properties;

  public JenniteVCFStore(String name, File directory, Properties properties) {
    this.name = name;
    this.directory = directory;
    this.properties = properties;
  }

  public String getName() {
    return name;
  }

  public Collection<String> getVCFNames() {
    List<String> names = new ArrayList<>();
    File[] children = directory.listFiles(File::isDirectory);
    if (children == null) return names;
    for (File child : children) {
      File dataFile = getVCFGZFile(child.getName());
      if (dataFile.exists()) names.add(child.getName());
    }
    return names;
  }

  public boolean hasVCF(String vcfName) {
    return getVCFGZFile(vcfName).exists();
  }

  public VCFSummary getVCFSummary(String vcfName) throws NoSuchElementException {
    if (!hasVCF(vcfName)) throw new NoSuchElementException("No VCF with name '" + vcfName + "' can be found");
    return JenniteVCFSummary.newSummary(vcfName).size(getVCFGZFile(vcfName)).samples(getSamplesFile(vcfName)).build();
  }

  /**
   * Prepare VCF folder and write VCF file, compress it if necessary and index it (requires VCF to be sorted).
   *
   * @param vcfName
   * @param vcf
   * @throws IOException
   */
  public void writeVCF(String vcfName, InputStream vcf) throws IOException {
    // writing a VCF is making a directory with the compressed and indexed VCF file
    String store;
    boolean isCompressed = false;
    if (vcfName.endsWith(".vcf")) store = vcfName.replaceAll("\\.vcf$", "");
    else if (vcfName.endsWith(".vcf.gz")) {
      store = vcfName.replaceAll("\\.vcf.gz$", "");
      isCompressed = true;
    }
    else throw new IllegalArgumentException("Not a VCF (.vcf) of compressed VCF (.vcf.gz) file: " + vcfName);

    // write data file, replacing anything that could be found at the VCF folder location
    File vcfFolder = getVCFFolder(store);
    if (vcfFolder.exists()) FileUtil.delete(vcfFolder);
    vcfFolder.mkdirs();
    File destination = isCompressed ? getVCFGZFile(store) : new File(getVCFFolder(store), VCF_FILE);
    Files.copy(vcf, destination.toPath());

    if (!isCompressed) compress(store);
    index(store);
    listSamples(store);
  }

  public void deleteVCF(String vcfName) {
    File store = getVCFFolder(vcfName);
    if (!store.exists()) return;
    try {
      FileUtil.delete(store);
    } catch (IOException e) {
      // ignore
    }
  }

  public OutputStream readVCF(String vcfName) throws NoSuchElementException, IOException {
    if (!hasVCF(vcfName)) throw new NoSuchElementException("No VCF with name '" + vcfName + "' can be found");
    return new FileOutputStream(getVCFGZFile(vcfName));
  }

  //
  // Private methods
  //

  private void compress(String vcfName) {
    // TODO check process status
    File dataFile = new File(getVCFFolder(vcfName), VCF_FILE);
    if (!dataFile.exists()) return;
    int status = runProcess(vcfName, bgzip("-f", dataFile.getAbsolutePath()));
  }

  private void index(String vcfName) {
    // TODO check process status
    int status = runProcess(vcfName, tabix("-f", "-p", "vcf", getVCFGZFile(vcfName).getAbsolutePath()));
  }

  private void listSamples(String vcfName) {
    // TODO check process status
    int status = runProcess(vcfName, bcftools("query", "--list-samples", getVCFGZFile(vcfName).getAbsolutePath()),
        ProcessBuilder.Redirect.to(getSamplesFile(vcfName)));
  }

  /**
   * Get VCF folder location.
   *
   * @param vcfName
   * @return
   */
  private File getVCFFolder(String vcfName) {
    return new File(directory, vcfName);
  }

  /**
   * Get VCF compressed file location.
   *
   * @param vcfName
   * @return
   */
  private File getVCFGZFile(String vcfName) {
    return new File(getVCFFolder(vcfName), VCF_GZ_FILE);
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

  private int runProcess(String vcfName, String[] command) {
    return runProcess(vcfName, command, null);
  }

  private int runProcess(String vcfName, String[] command, ProcessBuilder.Redirect redirect) {
    try {
      ProcessBuilder.Redirect red = redirect == null ? ProcessBuilder.Redirect.appendTo(new File(vcfName, EXEC_LOG)) : redirect;
      Process process = buildProcess(vcfName, command, red).start();
      return process.waitFor();
    } catch (Exception e) {
      return -1;
    }
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

  private String[] vcftools(String... args) {
    return getCommand("vcftools", args);
  }

  private String[] vcfsubset(String... args) {
    return getCommand("vcf-subset", args);
  }

  private String[] bcftools(String... args) {
    return getCommand("bcftools", args);
  }

  private String[] bgzip(String... args) {
    return getCommand("bgzip", args);
  }

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
