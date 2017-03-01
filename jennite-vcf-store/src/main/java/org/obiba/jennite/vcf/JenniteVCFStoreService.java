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
import org.obiba.opal.spi.vcf.VCFStoreService;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * VCF files are persisted by store. For each store there is a dedicated directory with a properties file and the VCF files.
 */
public class JenniteVCFStoreService implements VCFStoreService {

  private Properties properties;

  private boolean running;

  //
  // Service management
  //

  public String getName() {
    return "jennite";
  }

  public void configure(Properties properties) {
    this.properties = properties;
  }

  public boolean isRunning() {
    return running;
  }

  public void start() {
    // do init stuff
    running = true;
  }

  public void stop() {
    running = false;
  }

  //
  // Store methods
  //

  public Collection<String> getStoreNames() {
    List<String> names = new ArrayList<String>();
    for (File child : getDataFolder().listFiles()) {
      if (child.isDirectory()) names.add(child.getName());
    }
    return names;
  }

  public boolean hasStore(String name) {
    return getStoreFolder(name).exists();
  }

  public VCFStore getStore(String name) throws NoSuchElementException {
    return new JenniteVCFStore(name, getStoreFolder(name), properties);
  }

  public VCFStore createStore(String name) {
    File storeDir = getStoreFolder(name);
    if(!storeDir.exists()) storeDir.mkdirs();
    return new JenniteVCFStore(name, getStoreFolder(name), properties);
  }

  public void deleteStore(String name) {
    try {
      FileUtil.delete(getStoreFolder(name));
    } catch (IOException e) {
      // ignore
    }
  }

  //
  // Package methods
  //

  File getStoreFolder(String name) {
    return new File(getDataFolder(), name);
  }

  File getDataFolder() {
    checkStatus();
    String defaultDir = new File(".").getAbsolutePath();
    String dataDirPath = properties.getProperty(VCFStoreService.DATA_DIR_PROPERTY, defaultDir);
    File dataDir = new File(dataDirPath);
    if (!dataDir.exists()) dataDir.mkdirs();
    return dataDir;
  }

  //
  // Private methods
  //

  private void checkStatus() {
    if (!running) throw new IllegalStateException("Jennite VCF store service has not been started");
    if (properties == null) throw new IllegalStateException("Jennite VCF store service has not been configured");
  }
}
