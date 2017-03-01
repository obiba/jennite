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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.obiba.core.util.FileUtil;
import org.obiba.opal.spi.vcf.VCFStoreService;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class JenniteVCFStoreServiceTest {

  @Before
  public void setUp() throws IOException {
    FileUtil.delete(new File(getDefaultProperties().getProperty(VCFStoreService.DATA_DIR_PROPERTY)));
  }

  @After
  public void tearDown() throws IOException {
    FileUtil.delete(new File(getDefaultProperties().getProperty(VCFStoreService.DATA_DIR_PROPERTY)));
  }

  @Test(expected = IllegalStateException.class)
  public void testServiceNotStarted() {
    JenniteVCFStoreService service = new JenniteVCFStoreService();
    service.configure(new Properties());
    service.getDataFolder();
  }

  @Test(expected = IllegalStateException.class)
  public void testServiceNotConfigured() {
    JenniteVCFStoreService service = new JenniteVCFStoreService();
    service.start();
    service.getDataFolder();
  }

  @Test
  public void testService() {
    VCFStoreService service = createStoreService();
    Collection<String> stores = service.getStoreNames();
    assertThat(service.hasStore("foo")).isFalse();
    assertThat(stores.isEmpty()).isTrue();
    service.createStore("foo");
    assertThat(service.hasStore("foo")).isTrue();
    stores = service.getStoreNames();
    assertThat(stores.size()).isEqualTo(1);
    assertThat(stores.iterator().next()).isEqualTo("foo");
  }

  private VCFStoreService createStoreService() {
    JenniteVCFStoreService service = new JenniteVCFStoreService();
    service.configure(getDefaultProperties());
    service.start();
    System.out.println(service.getDataFolder().getAbsolutePath());
    return service;
  }

  private Properties getDefaultProperties() {
    Properties properties = new Properties();
    properties.setProperty(VCFStoreService.DATA_DIR_PROPERTY, "target" + File.separator + "test-stores");
    return properties;
  }

}
