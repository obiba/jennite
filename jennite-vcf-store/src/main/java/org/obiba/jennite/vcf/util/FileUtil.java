/*
 * Copyright (c) 2017 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.jennite.vcf.util;

import java.io.File;

public class FileUtil {

  public static void removeDirectory(File dir) {
    if (dir.isDirectory()) {
      File[] files = dir.listFiles();
      if (files != null && files.length > 0) {
        for (File aFile : files) {
          removeDirectory(aFile);
        }
      }
      dir.delete();
    } else {
      dir.delete();
    }
  }
}
