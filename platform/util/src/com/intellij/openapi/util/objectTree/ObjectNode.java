/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.util.objectTree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

public final class ObjectNode<T> {
  private static final ObjectNode[] EMPTY_ARRAY = new ObjectNode[0];

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectNode");

  private final ObjectTree<T> myTree;

  private ObjectNode<T> myParent;
  private final T myObject;

  private LinkedHashSet<ObjectNode<T>> myChildren;
  private final Throwable myTrace;

  private final long myOwnModification;
  private long myChildModification;

  public ObjectNode(@NotNull ObjectTree<T> tree, ObjectNode<T> parentNode, @NotNull T object, long modification) {
    myTree = tree;
    myParent = parentNode;
    myObject = object;

    myTrace = Disposer.isDebugMode() ? new Throwable() : null;
    myOwnModification = modification;
  }

  @NotNull
  private ObjectNode<T>[] getChildrenArray() {
    synchronized (myTree.treeLock) {
      if (myChildren == null || myChildren.isEmpty()) return EMPTY_ARRAY;
      return myChildren.toArray(new ObjectNode[myChildren.size()]);
    }
  }

  public void addChild(@NotNull ObjectNode<T> child) {
    synchronized (myTree.treeLock) {
      ensureChildArray();
      child.setParent(this);
      myChildren.add(child);
      myTree.putNode(child.getObject(), child);

      propagateChildModification(child.getModification());
    }
  }

  public void removeChild(@NotNull ObjectNode<T> child) {
    synchronized (myTree.treeLock) {
      assert myChildren != null: "No children to remove child: " + this + ' ' + child;
      if (myChildren.remove(child)) {
        child.setParent(null);
        myTree.putNode(child.getObject(), null);
        propagateChildModification(myTree.getNextModification());
      }
    }
  }

  private void setParent(ObjectNode<T> parent) {
    myParent = parent;
  }

  public ObjectNode<T> getParent() {
    return myParent;
  }

  @NotNull
  public Collection<ObjectNode<T>> getChildren() {
    synchronized (myTree.treeLock) {
      if (myChildren == null) return Collections.emptyList();
      return Collections.unmodifiableCollection(myChildren);
    }
  }

  private void ensureChildArray() {
    if (myChildren == null) {
      myChildren = new LinkedHashSet<ObjectNode<T>>(1);
    }
  }
  
  boolean execute(final boolean disposeTree, @NotNull final ObjectTreeAction<T> action) {
    ObjectTree.executeActionWithRecursiveGuard(this, myTree.getNodesInExecution(), new ObjectTreeAction<ObjectNode<T>>() {
      @Override
      public void execute(@NotNull ObjectNode<T> each) {
        action.beforeTreeExecution(myObject);

        ObjectNode<T>[] childrenArray = getChildrenArray();
        //todo: [kirillk] optimize

          for (int i = childrenArray.length - 1; i >= 0; i--) {
            childrenArray[i].execute(disposeTree, action);
          }

        if (disposeTree) {
          synchronized (myTree.treeLock) {
            myChildren = null;
          }
        }

        try {
          action.execute(myObject);
          myTree.fireExecuted(myObject);
        }
        catch (ProcessCanceledException e) {
          throw new ProcessCanceledException(e);
        }
        catch (Throwable e) {
          LOG.error(e);
        }

        if (disposeTree) {
          myTree.putNode(myObject, null);
          synchronized (myTree.treeLock) {
            if (myParent != null) {
              myParent.removeChild(ObjectNode.this);
            }
            else {
              myTree.removeRootObject(myObject);
            }
          }
        }
      }

      @Override
      public void beforeTreeExecution(@NotNull ObjectNode<T> parent) {

      }
    });

    return true;
  }

  @NotNull
  public T getObject() {
    return myObject;
  }

  @NonNls
  public String toString() {
    return "Node: " + myObject.toString();
  }

  Throwable getTrace() {
    return myTrace;
  }

  @TestOnly
  void assertNoReferencesKept(@NotNull T aDisposable) {
    assert getObject() != aDisposable;
    if (myChildren != null) {
      for (ObjectNode<T> node: myChildren) {
        node.assertNoReferencesKept(aDisposable);
      }
    }
  }

  public Throwable getAllocation() {
    return myTrace;
  }

  public long getOwnModification() {
    return myOwnModification;
  }

  public long getChildModification() {
    return myChildModification;
  }

  private void propagateChildModification(long stamp) {
    if (myChildModification < stamp) {
      myChildModification = stamp;
      if (getParent() != null) {
        getParent().propagateChildModification(stamp);
      }
    }
  }

  public long getModification() {
    return Math.max(getOwnModification(), getChildModification());
  }

  public <D extends Disposable> D findChildEqualTo(@NotNull D object) {
    LinkedHashSet<ObjectNode<T>> children = myChildren;
    if (children != null) {
      for (ObjectNode<T> node : children) {
        T nodeObject = node.getObject();
        if (nodeObject.equals(object)) {
          return (D)nodeObject;
        }
      }
    }
    return null;
  }
}
