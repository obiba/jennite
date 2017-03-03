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

import org.obiba.opal.spi.vcf.VCFStore;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class JenniteVCFSummary implements VCFStore.VCFSummary {

  private final String name;

  private List<String> sampleIds = new ArrayList<>();

  private long size;

  private JenniteVCFSummary(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Collection<String> sampleIds() {
    return sampleIds;
  }

  @Override
  public int variantsCount() {
    return 0;
  }

  @Override
  public int genotypesCount() {
    return 0;
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

    Builder size(File dataFile) {
      summary.size = dataFile.length();
      return this;
    }

    Builder samples(File samplesFile) {
      if (!samplesFile.exists()) return this;
      try (Stream<String> stream = Files.lines(samplesFile.toPath())) {
        stream.forEach(line -> summary.sampleIds.add(line));
      } catch (IOException e) {
        e.printStackTrace();
      }
      return this;
    }

    VCFStore.VCFSummary build() {
      return summary;
    }
  }
}
