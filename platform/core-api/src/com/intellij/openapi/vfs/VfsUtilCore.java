/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vfs.VirtualFileVisitor.Result;
import static com.intellij.openapi.vfs.VirtualFileVisitor.VisitorException;

public class VfsUtilCore {
  /**
   * Checks whether the <code>ancestor {@link com.intellij.openapi.vfs.VirtualFile}</code> is parent of <code>file
   * {@link com.intellij.openapi.vfs.VirtualFile}</code>.
   *
   * @param ancestor the file
   * @param file     the file
   * @param strict   if <code>false</code> then this method returns <code>true</code> if <code>ancestor</code>
   *                 and <code>file</code> are equal
   * @return <code>true</code> if <code>ancestor</code> is parent of <code>file</code>; <code>false</code> otherwise
   */
  public static boolean isAncestor(@NotNull VirtualFile ancestor, @NotNull VirtualFile file, boolean strict) {
    if (!file.getFileSystem().equals(ancestor.getFileSystem())) return false;
    VirtualFile parent = strict ? file.getParent() : file;
    while (true) {
      if (parent == null) return false;
      if (parent.equals(ancestor)) return true;
      parent = parent.getParent();
    }
  }

  public static boolean isAncestor(@NotNull File ancestor, @NotNull File file, boolean strict) {
    File parent = strict ? file.getParentFile() : file;
    while (parent != null) {
      if (parent.equals(ancestor)) return true;
      parent = parent.getParentFile();
    }

    return false;
  }

  /**
   * Gets the relative path of <code>file</code> to its <code>ancestor</code>. Uses <code>separator</code> for
   * separating files.
   *
   * @param file      the file
   * @param ancestor  parent file
   * @param separator character to use as files separator
   * @return the relative path or {@code null} if {@code ancestor} is not ancestor for {@code file}
   */
  @Nullable
  public static String getRelativePath(@NotNull VirtualFile file, @NotNull VirtualFile ancestor, char separator) {
    if (!file.getFileSystem().equals(ancestor.getFileSystem())) return null;

    int length = 0;
    VirtualFile parent = file;
    while (true) {
      if (parent == null) return null;
      if (parent.equals(ancestor)) break;
      if (length > 0) {
        length++;
      }
      length += parent.getName().length();
      parent = parent.getParent();
    }

    char[] chars = new char[length];
    int index = chars.length;
    parent = file;
    while (true) {
      if (parent.equals(ancestor)) break;
      if (index < length) {
        chars[--index] = separator;
      }
      String name = parent.getName();
      for (int i = name.length() - 1; i >= 0; i--) {
        chars[--index] = name.charAt(i);
      }
      parent = parent.getParent();
    }
    return new String(chars);
  }

  @Nullable
  public static VirtualFile getVirtualFileForJar(@Nullable VirtualFile entryVFile) {
    if (entryVFile == null) return null;
    final String path = entryVFile.getPath();
    final int separatorIndex = path.indexOf("!/");
    if (separatorIndex < 0) return null;

    String localPath = path.substring(0, separatorIndex);
    return VirtualFileManager.getInstance().findFileByUrl("file://" + localPath);
  }

  /**
   * Makes a copy of the <code>file</code> in the <code>toDir</code> folder and returns it.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link com.intellij.openapi.vfs.VirtualFileEvent#getRequestor}
   * @param file      file to make a copy of
   * @param toDir     directory to make a copy in
   * @return a copy of the file
   * @throws java.io.IOException if file failed to be copied
   */
  public static VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile toDir) throws IOException {
    return copyFile(requestor, file, toDir, file.getName());
  }

  /**
   * Makes a copy of the <code>file</code> in the <code>toDir</code> folder with the <code>newName</code> and returns it.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link com.intellij.openapi.vfs.VirtualFileEvent#getRequestor}
   * @param file      file to make a copy of
   * @param toDir     directory to make a copy in
   * @param newName   new name of the file
   * @return a copy of the file
   * @throws java.io.IOException if file failed to be copied
   */
  public static VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile toDir, @NotNull @NonNls String newName)
    throws IOException {
    final VirtualFile newChild = toDir.createChildData(requestor, newName);
    // [jeka] TODO: to be discussed if the copy should have the same timestamp as the original
    //OutputStream out = newChild.getOutputStream(requestor, -1, file.getActualTimeStamp());
    newChild.setBinaryContent(file.contentsToByteArray());
    return newChild;
  }

  @NotNull
  public static InputStream byteStreamSkippingBOM(@NotNull byte[] buf, @NotNull VirtualFile file) throws IOException {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") BufferExposingByteArrayInputStream stream = new BufferExposingByteArrayInputStream(buf);
    return inputStreamSkippingBOM(stream, file);
  }

  @NotNull
  public static InputStream inputStreamSkippingBOM(@NotNull InputStream stream, @NotNull VirtualFile file) throws IOException {
    return CharsetToolkit.inputStreamSkippingBOM(stream);
  }

  @NotNull
  public static OutputStream outputStreamAddingBOM(@NotNull OutputStream stream, @NotNull VirtualFile file) throws IOException {
    byte[] bom = file.getBOM();
    if (bom != null) {
      stream.write(bom);
    }
    return stream;
  }

  public static boolean iterateChildrenRecursively(@NotNull final VirtualFile root,
                                                   @Nullable final VirtualFileFilter filter,
                                                   @NotNull final ContentIterator iterator) {
    final Result result = visitChildrenRecursively(root, new VirtualFileVisitor() {
      @NotNull
      @Override
      public Result visitFileEx(@NotNull VirtualFile file) {
        if (filter != null && !filter.accept(file)) return SKIP_CHILDREN;
        if (!iterator.processFile(file)) return skipTo(root);
        return CONTINUE;
      }
    });
    return result.skipToParent != root;
  }

  @SuppressWarnings("UnsafeVfsRecursion")
  @NotNull
  public static Result visitChildrenRecursively(@NotNull VirtualFile file, @NotNull VirtualFileVisitor visitor) throws VisitorException {
    visitor.pushFrame();
    try {
      final boolean visited = visitor.allowVisitFile(file);
      if (visited) {
        Result result = visitor.visitFileEx(file);
        if (result.skipChildren) return result;
      }

      Iterable<VirtualFile> childrenIterable = null;

      final AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
      try {
        if (!file.isValid() || !visitor.allowVisitChildren(file)) return VirtualFileVisitor.CONTINUE;
        if (!visitor.depthLimitReached()) {
          childrenIterable = visitor.getChildrenIterable(file);
          if (childrenIterable == null) {
            childrenIterable = Arrays.asList(file.getChildren());
          }
        }
      }
      finally {
        token.finish();
      }

      if (childrenIterable != null) {
        for (VirtualFile child : childrenIterable) {
          Result result = visitChildrenRecursively(child, visitor);
          if (result.skipToParent != null && result.skipToParent != child) return result;
        }
      }

      if (visited) {
        visitor.afterChildrenVisited(file);
      }

      return VirtualFileVisitor.CONTINUE;
    }
    finally {
      visitor.popFrame();
    }
  }

  public static <E extends Exception> Result visitChildrenRecursively(@NotNull VirtualFile file,
                                                                      @NotNull VirtualFileVisitor visitor,
                                                                      @NotNull Class<E> eClass) throws E {
    try {
      return visitChildrenRecursively(file, visitor);
    }
    catch (VisitorException e) {
      final Throwable cause = e.getCause();
      if (eClass.isInstance(cause)) {
        throw eClass.cast(cause);
      }
      throw e;
    }
  }

  public static boolean isInvalidLink(@NotNull VirtualFile link) {
    final VirtualFile target = link.getCanonicalFile();
    return target == null || target == link || isAncestor(target, link, true);
  }

  @NotNull
  public static String loadText(@NotNull VirtualFile file) throws IOException{
    InputStreamReader reader = new InputStreamReader(file.getInputStream(), file.getCharset());
    try {
      return new String(FileUtil.loadText(reader, (int)file.getLength()));
    }
    finally {
      reader.close();
    }
  }

  @NotNull
  public static String loadText(@NotNull VirtualFile file, int length) throws IOException{
    InputStreamReader reader = new InputStreamReader(file.getInputStream(), file.getCharset());
    try {
      return new String(FileUtil.loadText(reader, length));
    }
    finally {
      reader.close();
    }
  }

  @NotNull
  public static VirtualFile[] toVirtualFileArray(@NotNull Collection<? extends VirtualFile> files) {
    int size = files.size();
    if (size == 0) return VirtualFile.EMPTY_ARRAY;
    //noinspection SSBasedInspection
    return files.toArray(new VirtualFile[size]);
  }

  @NotNull
  public static String urlToPath(@NonNls @Nullable String url) {
    if (url == null) return "";
    return VirtualFileManager.extractPath(url);
  }

  @NotNull
  public static File virtualToIoFile(@NotNull VirtualFile file) {
    return new File(PathUtil.toPresentableUrl(file.getUrl()));
  }

  @NotNull
  public static String pathToUrl(@NonNls @NotNull String path) {
    return VirtualFileManager.constructUrl(StandardFileSystems.FILE_PROTOCOL, path);
  }

  public static List<File> virtualToIoFiles(Collection<VirtualFile> scope) {
    return ContainerUtil.map2List(scope, new Function<VirtualFile, File>() {
      @Override
      public File fun(VirtualFile file) {
        return virtualToIoFile(file);
      }
    });
  }
}
