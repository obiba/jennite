/*
 * Copyright (c) 2018 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.jennite.vcf;

import org.obiba.opal.spi.vcf.VCFStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

public class JenniteVCFSummary implements VCFStore.VCFSummary {

  private static final Logger log = LoggerFactory.getLogger(JenniteVCFSummary.class);

  private static final String SN_RECORDS = "SN\t0\tnumber of records:\t";

  private static final String SN_SAMPLES = "SN\t0\tnumber of samples:\t";

  private final String name;

  private VCFStore.Format format;

  private List<String> sampleIds = new ArrayList<>();

  private int variantsCount = 0;

  private long genotypesCount = 0;

  private long size;

  private JenniteVCFSummary(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public VCFStore.Format getFormat() {
    return format;
  }

  @Override
  public Collection<String> getSampleIds() {
    return sampleIds;
  }

  @Override
  public int getVariantsCount() {
    return variantsCount;
  }

  @Override
  public int getGenotypesCount() {
    // broken when bigger than a int
    return (int)genotypesCount;
  }

  @Override
  public long size() {
    return size;
  }

  static Builder newSummary(String name) {
    return new Builder(name);
  }

  static class Builder {
    private final JenniteVCFSummary summary;

    Builder(String name) {
      this.summary = new JenniteVCFSummary(name);
    }

    Builder format(VCFStore.Format format) {
      summary.format = format;
      return this;
    }

    Builder size(File dataFile) {
      summary.size = dataFile.length();
      return this;
    }

    Builder samples(File samplesFile) {
      if (!samplesFile.exists()) return this;
      try (Stream<String> stream = Files.lines(samplesFile.toPath())) {
        stream.forEach(line -> summary.sampleIds.add(line));
      } catch (IOException e) {
        log.error("Unable to read samples file: {}", samplesFile.getAbsolutePath(), e);
      }
      return this;
    }

    Builder statistics(File statsFile) {
      if (!statsFile.exists()) return this;
      try (BufferedReader br = new BufferedReader(new FileReader(statsFile))) {
        String line;
        boolean stop = false;
        int samplesCount = 0;
        while (!stop && (line = br.readLine()) != null) {
          try {
            if (line.startsWith(SN_RECORDS)) {
              summary.variantsCount = Integer.parseInt(line.replace(SN_RECORDS, ""));
            } else if (line.startsWith(SN_SAMPLES)) {
              samplesCount = Integer.parseInt(line.replace(SN_SAMPLES, ""));
            }
          } catch (NumberFormatException e) {
            // ignore
          }
          stop = summary.variantsCount != 0 && samplesCount != 0;
        }
        // approximate: ignore missing calls
        summary.genotypesCount = samplesCount * (long)summary.variantsCount;
      } catch (IOException e) {
        log.error("Unable to read statistics file: {}", statsFile.getAbsolutePath(), e);
      }
      return this;
    }

    Builder properties(File vcfPropertiesFile) {
      try (InputStream in = new FileInputStream(vcfPropertiesFile)) {
        Properties prop = new Properties();
        prop.load(in);
        String version = prop.getProperty("version");
        Long genotypesCount = Long.parseLong(prop.getProperty("summary.genotypes.count"));
        summary.variantsCount = Integer.parseInt(prop.getProperty("summary.variants.count"));
        summary.size = Long.parseLong(prop.getProperty("summary.size"));
        summary.format = VCFStore.Format.valueOf(prop.getProperty("summary.format", "VCF"));
        // fix counts issue in 1.0.0
        if ("1.0.0".equals(version) || version.startsWith("0.1")) {
          summary.variantsCount = genotypesCount.intValue();
          summary.genotypesCount = Long.parseLong(prop.getProperty("summary.samples.count")) * (long)summary.variantsCount;
        } else {
          summary.genotypesCount = genotypesCount;
        }
      } catch (IOException e) {
        log.error("Unable to read properties file: {}", vcfPropertiesFile.getAbsolutePath(), e);
      }
      return this;
    }

    VCFStore.VCFSummary build() {
      return summary;
    }

  }
}
