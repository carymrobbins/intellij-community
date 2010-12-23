package com.jetbrains.python.debugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.ui.DebuggerIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

// todo: load long lists by parts
// todo: null modifier for modify modules, class objects etc.
public class PyDebugValue extends XValue {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.PyDebugValue");
  public static final int MAX_VALUE = 512;

  private final String myName;
  private String myTempName = null;
  private final String myType;
  private final String myValue;
  private final boolean myContainer;
  private final PyDebugValue myParent;
  private final IPyDebugProcess myDebugProcess;

  public PyDebugValue(final String name, final String type, final String value, final boolean container) {
    this(name, type, value, container, null, null);
  }

  public PyDebugValue(final String name, final String type, final String value, final boolean container,
                      final PyDebugValue parent, final IPyDebugProcess debugProcess) {
    myName = name;
    myType = type;
    myValue = value;
    myContainer = container;
    myParent = parent;
    myDebugProcess = debugProcess;
  }

  public String getName() {
    return myName;
  }

  public String getTempName() {
    return myTempName != null ? myTempName : myName;
  }

  public void setTempName(String tempName) {
    myTempName = tempName;
  }

  public String getType() {
    return myType;
  }

  public String getValue() {
    return myValue;
  }

  public boolean isContainer() {
    return myContainer;
  }

  public PyDebugValue getParent() {
    return myParent;
  }

  public PyDebugValue getTopParent() {
    return myParent == null ? this : myParent.getTopParent();
  }

  public String getEvaluationExpression() {
    StringBuilder stringBuilder = new StringBuilder();
    buildExpression(stringBuilder);
    return stringBuilder.toString();
  }

  void buildExpression(StringBuilder result) {
    if (myParent == null) {
      result.append(getTempName());
    }
    else {
      myParent.buildExpression(result);
      if (("dict".equals(myParent.getType()) || "list".equals(myParent.getType()) || "tuple".equals(myParent.getType())) &&
          !isLen(myName)) {
        result.append('[').append(removeId(myName)).append(']');
      }
      else if (("set".equals(myParent.getType())) && !isLen(myName)) {
        //set doesn't support indexing
      }
      else if (isLen(myName)) {
        result.append('.').append(myName).append("()");
      }
      else {
        result.append('.').append(myName);
      }
    }
  }

  private static String removeId(@NotNull String name) {
    if (name.indexOf('(') != -1) {
      name = name.substring(0, name.indexOf('(')).trim();
    }

    return name;
  }

  private static boolean isLen(String name) {
    return "__len__".equals(name);
  }

  @Override
  public void computePresentation(@NotNull final XValueNode node) {
    String value = PyTypeHandler.format(this);

    if (value.length() >= MAX_VALUE) {
      node.setFullValueEvaluator(new PyFullValueEvaluator("Show full value", myDebugProcess, myName));
      value = value.substring(0, MAX_VALUE) + "...";
    }
    node.setPresentation(myName, getValueIcon(), myType, value, myContainer);
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (node.isObsolete()) return;
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        if (myDebugProcess == null) return;

        try {
          final List<PyDebugValue> values = myDebugProcess.loadVariable(PyDebugValue.this);
          if (!node.isObsolete()) {
            node.addChildren(values, true);
          }
        }
        catch (PyDebuggerException e) {
          if (!node.isObsolete()) {
            node.setErrorMessage("Unable to display children:" + e.getMessage());
          }
          LOG.warn(e);
        }
      }
    });
  }

  @Override
  public XValueModifier getModifier() {
    return new PyValueModifier(myDebugProcess, this);
  }

  private Icon getValueIcon() {
    if (!myContainer) {
      return DebuggerIcons.PRIMITIVE_VALUE_ICON;
    }
    else if ("list".equals(myType) || "tuple".equals(myType)) {
      return DebuggerIcons.ARRAY_VALUE_ICON;
    }
    else {
      return DebuggerIcons.VALUE_ICON;
    }
  }

  boolean isException() {
    return myValue.startsWith("Traceback (most recent call last):");
  }
}
