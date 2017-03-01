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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Properties;

public class JenniteVCFStore implements VCFStore {

  private final String name;

  private final Properties properties;

  public JenniteVCFStore(String name, Properties properties) {
    this.name = name;
    this.properties = properties;
  }

  public String getName() {
    return name;
  }

  public Collection<String> getVCFNames() {
    return null;
  }

  public boolean hasVCF(String name) {
    return false;
  }

  public VCFSummary getVCFSummary(String name) throws NoSuchElementException {
    return null;
  }

  public void writeVCF(String name, InputStream vcf) {

  }

  public void deleteVCF(String name) {

  }

  public OutputStream readVCF(String name) throws NoSuchElementException {
    return null;
  }
}
