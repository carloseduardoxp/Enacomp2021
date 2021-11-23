package gr.uom.java.xmi.diff;

import gr.uom.java.xmi.UMLAnnotation;
import gr.uom.java.xmi.UMLOperation;
import gr.uom.java.xmi.UMLParameter;
import gr.uom.java.xmi.UMLType;
import gr.uom.java.xmi.decomposition.AbstractCodeMapping;
import gr.uom.java.xmi.decomposition.UMLOperationBodyMapper;
import gr.uom.java.xmi.decomposition.VariableDeclaration;
import gr.uom.java.xmi.decomposition.VariableReferenceExtractor;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.refactoringminer.api.Refactoring;

public class UMLOperationDiff {
	private UMLOperation removedOperation;
	private UMLOperation addedOperation;
	private List<UMLParameter> addedParameters;
	private List<UMLParameter> removedParameters;
	private List<UMLParameterDiff> parameterDiffList;
	private boolean visibilityChanged;
	private boolean abstractionChanged;
	private boolean finalChanged;
	private boolean staticChanged;
	private boolean synchronizedChanged;
	private boolean returnTypeChanged;
	private boolean qualifiedReturnTypeChanged;
	private boolean operationRenamed;
	private boolean parametersReordered;
	private Set<AbstractCodeMapping> mappings = new LinkedHashSet<AbstractCodeMapping>();
	private Set<Pair<VariableDeclaration, VariableDeclaration>> matchedVariables = new LinkedHashSet<>();
	private Set<Refactoring> refactorings = new LinkedHashSet<>();
	private UMLAnnotationListDiff annotationListDiff;
	private List<UMLType> addedExceptionTypes;
	private List<UMLType> removedExceptionTypes;
	private SimpleEntry<Set<UMLType>, Set<UMLType>> changedExceptionTypes;
	
	public UMLOperationDiff(UMLOperation removedOperation, UMLOperation addedOperation) {
		process(removedOperation, addedOperation);
	}

	private void process(UMLOperation removedOperation, UMLOperation addedOperation) {
		this.removedOperation = removedOperation;
		this.addedOperation = addedOperation;
		this.addedParameters = new ArrayList<UMLParameter>();
		this.removedParameters = new ArrayList<UMLParameter>();
		this.parameterDiffList = new ArrayList<UMLParameterDiff>();
		this.addedExceptionTypes = new ArrayList<UMLType>();
		this.removedExceptionTypes = new ArrayList<UMLType>();
		this.visibilityChanged = false;
		this.abstractionChanged = false;
		this.returnTypeChanged = false;
		this.operationRenamed = false;
		if(!removedOperation.getName().equals(addedOperation.getName()))
			operationRenamed = true;
		if(!removedOperation.getVisibility().equals(addedOperation.getVisibility()))
			visibilityChanged = true;
		if(removedOperation.isAbstract() != addedOperation.isAbstract())
			abstractionChanged = true;
		if(removedOperation.isFinal() != addedOperation.isFinal())
			finalChanged = true;
		if(removedOperation.isStatic() != addedOperation.isStatic())
			staticChanged = true;
		if(removedOperation.isSynchronized() != addedOperation.isSynchronized())
			synchronizedChanged = true;
		if(!removedOperation.equalReturnParameter(addedOperation))
			returnTypeChanged = true;
		else if(!removedOperation.equalQualifiedReturnParameter(addedOperation))
			qualifiedReturnTypeChanged = true;
		processThrownExceptionTypes(removedOperation.getThrownExceptionTypes(), addedOperation.getThrownExceptionTypes());
		this.annotationListDiff = new UMLAnnotationListDiff(removedOperation.getAnnotations(), addedOperation.getAnnotations());
		List<SimpleEntry<UMLParameter, UMLParameter>> matchedParameters = updateAddedRemovedParameters(removedOperation, addedOperation);
		for(SimpleEntry<UMLParameter, UMLParameter> matchedParameter : matchedParameters) {
			UMLParameter parameter1 = matchedParameter.getKey();
			UMLParameter parameter2 = matchedParameter.getValue();
			UMLParameterDiff parameterDiff = new UMLParameterDiff(parameter1, parameter2, removedOperation, addedOperation, mappings);
			if(!parameterDiff.isEmpty()) {
				parameterDiffList.add(parameterDiff);
			}
		}
		int matchedParameterCount = matchedParameters.size()/2;
		List<String> parameterNames1 = removedOperation.getParameterNameList();
		List<String> parameterNames2 = addedOperation.getParameterNameList();
		if(removedParameters.isEmpty() && addedParameters.isEmpty() &&
				matchedParameterCount == parameterNames1.size() && matchedParameterCount == parameterNames2.size() &&
				parameterNames1.size() == parameterNames2.size() && parameterNames1.size() > 1 && !parameterNames1.equals(parameterNames2)) {
			parametersReordered = true;
		}
		//first round match parameters with the same name
		for(Iterator<UMLParameter> removedParameterIterator = removedParameters.iterator(); removedParameterIterator.hasNext();) {
			UMLParameter removedParameter = removedParameterIterator.next();
			for(Iterator<UMLParameter> addedParameterIterator = addedParameters.iterator(); addedParameterIterator.hasNext();) {
				UMLParameter addedParameter = addedParameterIterator.next();
				if(removedParameter.getName().equals(addedParameter.getName())) {
					UMLParameterDiff parameterDiff = new UMLParameterDiff(removedParameter, addedParameter, removedOperation, addedOperation, mappings);
					if(!parameterDiff.isEmpty()) {
						parameterDiffList.add(parameterDiff);
					}
					addedParameterIterator.remove();
					removedParameterIterator.remove();
					break;
				}
			}
		}
		//second round match parameters with the same type
		for(Iterator<UMLParameter> removedParameterIterator = removedParameters.iterator(); removedParameterIterator.hasNext();) {
			UMLParameter removedParameter = removedParameterIterator.next();
			for(Iterator<UMLParameter> addedParameterIterator = addedParameters.iterator(); addedParameterIterator.hasNext();) {
				UMLParameter addedParameter = addedParameterIterator.next();
				if(removedParameter.getType().equalsQualified(addedParameter.getType()) &&
						!existsAnotherAddedParameterWithTheSameType(addedParameter)) {
					UMLParameterDiff parameterDiff = new UMLParameterDiff(removedParameter, addedParameter, removedOperation, addedOperation, mappings);
					if(!parameterDiff.isEmpty()) {
						parameterDiffList.add(parameterDiff);
					}
					addedParameterIterator.remove();
					removedParameterIterator.remove();
					break;
				}
			}
		}
		//third round match parameters with different type and name
		List<UMLParameter> removedParametersWithoutReturnType = removedOperation.getParametersWithoutReturnType();
		List<UMLParameter> addedParametersWithoutReturnType = addedOperation.getParametersWithoutReturnType();
		if(matchedParameterCount == removedParametersWithoutReturnType.size()-1 && matchedParameterCount == addedParametersWithoutReturnType.size()-1) {
			for(Iterator<UMLParameter> removedParameterIterator = removedParameters.iterator(); removedParameterIterator.hasNext();) {
				UMLParameter removedParameter = removedParameterIterator.next();
				int indexOfRemovedParameter = indexOfParameter(removedParametersWithoutReturnType, removedParameter);
				for(Iterator<UMLParameter> addedParameterIterator = addedParameters.iterator(); addedParameterIterator.hasNext();) {
					UMLParameter addedParameter = addedParameterIterator.next();
					int indexOfAddedParameter = indexOfParameter(addedParametersWithoutReturnType, addedParameter);
					if(indexOfRemovedParameter == indexOfAddedParameter &&
							usedParameters(removedOperation, addedOperation, removedParameter, addedParameter)) {
						UMLParameterDiff parameterDiff = new UMLParameterDiff(removedParameter, addedParameter, removedOperation, addedOperation, mappings);
						if(!parameterDiff.isEmpty()) {
							parameterDiffList.add(parameterDiff);
						}
						addedParameterIterator.remove();
						removedParameterIterator.remove();
						break;
					}
				}
			}
		}
	}

	private boolean usedParameters(UMLOperation removedOperation, UMLOperation addedOperation,
			UMLParameter removedParameter, UMLParameter addedParameter) {
		List<String> removedOperationVariables = removedOperation.getAllVariables();
		List<String> addedOperationVariables = addedOperation.getAllVariables();
		if(removedOperationVariables.contains(removedParameter.getName()) ==
				addedOperationVariables.contains(addedParameter.getName())) {
			if(!removedOperation.isConstructor() && !addedOperation.isConstructor()) {
				return !removedOperationVariables.contains(addedParameter.getName()) &&
						!addedOperationVariables.contains(removedParameter.getName());
			}
			else {
				return true;
			}
		}
		return false;
	}

	private int indexOfParameter(List<UMLParameter> parameters, UMLParameter parameter) {
		int index = 0;
		for(UMLParameter p : parameters) {
			if(p.equalsIncludingName(parameter)) {
				return index;
			}
			index++;
		}
		return -1;
	}

	public UMLOperationDiff(UMLOperationBodyMapper mapper) {
		this.mappings = mapper.getMappings();
		this.matchedVariables = mapper.getMatchedVariables();
		this.refactorings = mapper.getRefactoringsAfterPostProcessing();
		process(mapper.getOperation1(), mapper.getOperation2());
	}

	private void processThrownExceptionTypes(List<UMLType> exceptionTypes1, List<UMLType> exceptionTypes2) {
		Set<UMLType> addedExceptionTypes = new LinkedHashSet<UMLType>();
		Set<UMLType> removedExceptionTypes = new LinkedHashSet<UMLType>();
		for(UMLType exceptionType1 : exceptionTypes1) {
			boolean found = false;
			for(UMLType exceptionType2 : exceptionTypes2) {
				if(exceptionType1.equals(exceptionType2)) {
					found = true;
					break;
				}
			}
			if(!found) {
				removedExceptionTypes.add(exceptionType1);
			}
		}
		for(UMLType exceptionType2 : exceptionTypes2) {
			boolean found = false;
			for(UMLType exceptionType1 : exceptionTypes1) {
				if(exceptionType1.equals(exceptionType2)) {
					found = true;
					break;
				}
			}
			if(!found) {
				addedExceptionTypes.add(exceptionType2);
			}
		}
		if(removedExceptionTypes.size() > 0 && addedExceptionTypes.size() == 0) {
			this.removedExceptionTypes.addAll(removedExceptionTypes);
		}
		else if(addedExceptionTypes.size() > 0 && removedExceptionTypes.size() == 0) {
			this.addedExceptionTypes.addAll(addedExceptionTypes);
		}
		else if(removedExceptionTypes.size() > 0 && addedExceptionTypes.size() > 0) {
			this.changedExceptionTypes = new SimpleEntry<Set<UMLType>, Set<UMLType>>(removedExceptionTypes, addedExceptionTypes);
		}
	}

	private boolean existsAnotherAddedParameterWithTheSameType(UMLParameter parameter) {
		if(removedOperation.hasTwoParametersWithTheSameType() && addedOperation.hasTwoParametersWithTheSameType()) {
			return false;
		}
		for(UMLParameter addedParameter : addedParameters) {
			if(!addedParameter.getName().equals(parameter.getName()) &&
					addedParameter.getType().equalsQualified(parameter.getType())) {
				return true;
			}
		}
		return false;
	}

	private List<SimpleEntry<UMLParameter, UMLParameter>> updateAddedRemovedParameters(UMLOperation removedOperation, UMLOperation addedOperation) {
		List<SimpleEntry<UMLParameter, UMLParameter>> matchedParameters = new ArrayList<SimpleEntry<UMLParameter, UMLParameter>>();
		for(UMLParameter parameter1 : removedOperation.getParameters()) {
			if(!parameter1.getKind().equals("return")) {
				boolean found = false;
				for(UMLParameter parameter2 : addedOperation.getParameters()) {
					if(parameter1.equalsIncludingName(parameter2)) {
						matchedParameters.add(new SimpleEntry<UMLParameter, UMLParameter>(parameter1, parameter2));
						found = true;
						break;
					}
				}
				if(!found) {
					this.removedParameters.add(parameter1);
				}
			}
		}
		for(UMLParameter parameter1 : addedOperation.getParameters()) {
			if(!parameter1.getKind().equals("return")) {
				boolean found = false;
				for(UMLParameter parameter2 : removedOperation.getParameters()) {
					if(parameter1.equalsIncludingName(parameter2)) {
						matchedParameters.add(new SimpleEntry<UMLParameter, UMLParameter>(parameter2, parameter1));
						found = true;
						break;
					}
				}
				if(!found) {
					this.addedParameters.add(parameter1);
				}
			}
		}
		return matchedParameters;
	}

	public List<UMLParameterDiff> getParameterDiffList() {
		return parameterDiffList;
	}

	public UMLOperation getRemovedOperation() {
		return removedOperation;
	}

	public UMLOperation getAddedOperation() {
		return addedOperation;
	}

	public List<UMLParameter> getAddedParameters() {
		return addedParameters;
	}

	public List<UMLParameter> getRemovedParameters() {
		return removedParameters;
	}

	public boolean isOperationRenamed() {
		return operationRenamed;
	}

	public boolean isEmpty() {
		return addedParameters.isEmpty() && removedParameters.isEmpty() && parameterDiffList.isEmpty() &&
		!visibilityChanged && !abstractionChanged && !finalChanged && !staticChanged && !synchronizedChanged && !returnTypeChanged && !operationRenamed && annotationListDiff.isEmpty();
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		if(!isEmpty())
			sb.append("\t").append(removedOperation).append("\n");
		if(operationRenamed)
			sb.append("\t").append("renamed from " + removedOperation.getName() + " to " + addedOperation.getName()).append("\n");
		if(visibilityChanged)
			sb.append("\t").append("visibility changed from " + removedOperation.getVisibility() + " to " + addedOperation.getVisibility()).append("\n");
		if(abstractionChanged)
			sb.append("\t").append("abstraction changed from " + (removedOperation.isAbstract() ? "abstract" : "concrete") + " to " +
					(addedOperation.isAbstract() ? "abstract" : "concrete")).append("\n");
		if(returnTypeChanged || qualifiedReturnTypeChanged)
			sb.append("\t").append("return type changed from " + removedOperation.getReturnParameter() + " to " + addedOperation.getReturnParameter()).append("\n");
		for(UMLParameter umlParameter : removedParameters) {
			sb.append("\t").append("parameter " + umlParameter + " removed").append("\n");
		}
		for(UMLParameter umlParameter : addedParameters) {
			sb.append("\t").append("parameter " + umlParameter + " added").append("\n");
		}
		for(UMLParameterDiff parameterDiff : parameterDiffList) {
			sb.append(parameterDiff);
		}
		for(UMLAnnotation annotation : annotationListDiff.getRemovedAnnotations()) {
			sb.append("\t").append("annotation " + annotation + " removed").append("\n");
		}
		for(UMLAnnotation annotation : annotationListDiff.getAddedAnnotations()) {
			sb.append("\t").append("annotation " + annotation + " added").append("\n");
		}
		for(UMLAnnotationDiff annotationDiff : annotationListDiff.getAnnotationDiffList()) {
			sb.append("\t").append("annotation " + annotationDiff.getRemovedAnnotation() + " modified to " + annotationDiff.getAddedAnnotation()).append("\n");
		}
		return sb.toString();
	}

	public Set<Refactoring> getRefactorings() {
		Set<Refactoring> refactorings = new LinkedHashSet<Refactoring>();
		if(returnTypeChanged || qualifiedReturnTypeChanged) {
			UMLParameter removedOperationReturnParameter = removedOperation.getReturnParameter();
			UMLParameter addedOperationReturnParameter = addedOperation.getReturnParameter();
			if(removedOperationReturnParameter != null && addedOperationReturnParameter != null) {
				Set<AbstractCodeMapping> references = VariableReferenceExtractor.findReturnReferences(mappings);
				ChangeReturnTypeRefactoring refactoring = new ChangeReturnTypeRefactoring(removedOperationReturnParameter.getType(), addedOperationReturnParameter.getType(),
						removedOperation, addedOperation, references);
				refactorings.add(refactoring);
			}
		}
		for(UMLParameterDiff parameterDiff : getParameterDiffList()) {
			boolean conflictFound = false;
			for(Pair<VariableDeclaration, VariableDeclaration> matchedPair : matchedVariables) {
				if(matchedPair.getLeft().equals(parameterDiff.getRemovedParameter().getVariableDeclaration()) &&
						!matchedPair.getRight().equals(parameterDiff.getAddedParameter().getVariableDeclaration())) {
					conflictFound = true;
					if(matchedPair.getLeft().isParameter() && matchedPair.getRight().isLocalVariable()) {
						Refactoring rename = new RenameVariableRefactoring(matchedPair.getLeft(), matchedPair.getRight(), removedOperation, addedOperation,
								VariableReferenceExtractor.findReferences(matchedPair.getLeft(), matchedPair.getRight(), mappings), false);
						refactorings.add(rename);
						Refactoring addParameter = new AddParameterRefactoring(parameterDiff.getAddedParameter(), removedOperation, addedOperation);
						refactorings.add(addParameter);
					}
					break;
				}
				if(matchedPair.getRight().equals(parameterDiff.getAddedParameter().getVariableDeclaration()) &&
						!matchedPair.getLeft().equals(parameterDiff.getRemovedParameter().getVariableDeclaration())) {
					conflictFound = true;
					if(matchedPair.getLeft().isLocalVariable() && matchedPair.getRight().isParameter()) {
						Refactoring rename = new RenameVariableRefactoring(matchedPair.getLeft(), matchedPair.getRight(), removedOperation, addedOperation,
								VariableReferenceExtractor.findReferences(matchedPair.getLeft(), matchedPair.getRight(), mappings), false);
						refactorings.add(rename);
						Refactoring removeParameter = new RemoveParameterRefactoring(parameterDiff.getRemovedParameter(), removedOperation, addedOperation);
						refactorings.add(removeParameter);
					}
					break;
				}
			}
			for(Refactoring refactoring : this.refactorings) {
				if(refactoring instanceof RenameVariableRefactoring) {
					RenameVariableRefactoring rename = (RenameVariableRefactoring)refactoring;
					if(rename.getOriginalVariable().equals(parameterDiff.getRemovedParameter().getVariableDeclaration()) &&
							!rename.getRenamedVariable().equals(parameterDiff.getAddedParameter().getVariableDeclaration())) {
						conflictFound = true;
						break;
					}
					else if(!rename.getOriginalVariable().equals(parameterDiff.getRemovedParameter().getVariableDeclaration()) &&
							rename.getRenamedVariable().equals(parameterDiff.getAddedParameter().getVariableDeclaration())) {
						conflictFound = true;
						break;
					}
				}
				else if(refactoring instanceof ChangeVariableTypeRefactoring) {
					ChangeVariableTypeRefactoring changeType = (ChangeVariableTypeRefactoring)refactoring;
					if(changeType.getOriginalVariable().equals(parameterDiff.getRemovedParameter().getVariableDeclaration()) &&
							!changeType.getChangedTypeVariable().equals(parameterDiff.getAddedParameter().getVariableDeclaration())) {
						conflictFound = true;
						break;
					}
					else if(!changeType.getOriginalVariable().equals(parameterDiff.getRemovedParameter().getVariableDeclaration()) &&
							changeType.getChangedTypeVariable().equals(parameterDiff.getAddedParameter().getVariableDeclaration())) {
						conflictFound = true;
						break;
					}
				}
				else if(refactoring instanceof MergeVariableRefactoring) {
					MergeVariableRefactoring merge = (MergeVariableRefactoring)refactoring;
					if(merge.getMergedVariables().contains(parameterDiff.getRemovedParameter().getVariableDeclaration()) &&
							merge.getNewVariable().equals(parameterDiff.getAddedParameter().getVariableDeclaration())) {
						conflictFound = true;
						break;
					}
				}
			}
			if(!conflictFound) {
				refactorings.addAll(parameterDiff.getRefactorings());
			}
		}
		int exactMappings = 0;
		for(AbstractCodeMapping mapping : mappings) {
			if(mapping.isExact()) {
				exactMappings++;
			}
		}
		if(removedParameters.isEmpty() || exactMappings > 0) {
			for(UMLParameter umlParameter : addedParameters) {
				boolean conflictFound = false;
				for(Refactoring refactoring : this.refactorings) {
					if(refactoring instanceof RenameVariableRefactoring) {
						RenameVariableRefactoring rename = (RenameVariableRefactoring)refactoring;
						if(rename.getRenamedVariable().equals(umlParameter.getVariableDeclaration())) {
							conflictFound = true;
							break;
						}
					}
					else if(refactoring instanceof ChangeVariableTypeRefactoring) {
						ChangeVariableTypeRefactoring changeType = (ChangeVariableTypeRefactoring)refactoring;
						if(changeType.getChangedTypeVariable().equals(umlParameter.getVariableDeclaration())) {
							conflictFound = true;
							break;
						}
					}
				}
				if(!conflictFound) {
					AddParameterRefactoring refactoring = new AddParameterRefactoring(umlParameter, removedOperation, addedOperation);
					refactorings.add(refactoring);
				}
			}
		}
		if(addedParameters.isEmpty() || exactMappings > 0) {
			for(UMLParameter umlParameter : removedParameters) {
				boolean conflictFound = false;
				for(Refactoring refactoring : this.refactorings) {
					if(refactoring instanceof RenameVariableRefactoring) {
						RenameVariableRefactoring rename = (RenameVariableRefactoring)refactoring;
						if(rename.getOriginalVariable().equals(umlParameter.getVariableDeclaration())) {
							conflictFound = true;
							break;
						}
					}
					else if(refactoring instanceof ChangeVariableTypeRefactoring) {
						ChangeVariableTypeRefactoring changeType = (ChangeVariableTypeRefactoring)refactoring;
						if(changeType.getOriginalVariable().equals(umlParameter.getVariableDeclaration())) {
							conflictFound = true;
							break;
						}
					}
				}
				if(!conflictFound) {
					RemoveParameterRefactoring refactoring = new RemoveParameterRefactoring(umlParameter, removedOperation, addedOperation);
					refactorings.add(refactoring);
				}
			}
		}
		if(parametersReordered) {
			ReorderParameterRefactoring refactoring = new ReorderParameterRefactoring(removedOperation, addedOperation);
			refactorings.add(refactoring);
		}
		for(UMLAnnotation annotation : annotationListDiff.getAddedAnnotations()) {
			AddMethodAnnotationRefactoring refactoring = new AddMethodAnnotationRefactoring(annotation, removedOperation, addedOperation);
			refactorings.add(refactoring);
		}
		for(UMLAnnotation annotation : annotationListDiff.getRemovedAnnotations()) {
			RemoveMethodAnnotationRefactoring refactoring = new RemoveMethodAnnotationRefactoring(annotation, removedOperation, addedOperation);
			refactorings.add(refactoring);
		}
		for(UMLAnnotationDiff annotationDiff : annotationListDiff.getAnnotationDiffList()) {
			ModifyMethodAnnotationRefactoring refactoring = new ModifyMethodAnnotationRefactoring(annotationDiff.getRemovedAnnotation(), annotationDiff.getAddedAnnotation(), removedOperation, addedOperation);
			refactorings.add(refactoring);
		}
		for(UMLType exceptionType : addedExceptionTypes) {
			AddThrownExceptionTypeRefactoring refactoring = new AddThrownExceptionTypeRefactoring(exceptionType, removedOperation, addedOperation);
			refactorings.add(refactoring);
		}
		for(UMLType exceptionType : removedExceptionTypes) {
			RemoveThrownExceptionTypeRefactoring refactoring = new RemoveThrownExceptionTypeRefactoring(exceptionType, removedOperation, addedOperation);
			refactorings.add(refactoring);
		}
		if(changedExceptionTypes != null) {
			ChangeThrownExceptionTypeRefactoring refactoring = new ChangeThrownExceptionTypeRefactoring(changedExceptionTypes.getKey(), changedExceptionTypes.getValue(), removedOperation, addedOperation);
			refactorings.add(refactoring);
		}
		if(visibilityChanged) {
			ChangeOperationAccessModifierRefactoring refactoring = new ChangeOperationAccessModifierRefactoring(removedOperation.getVisibility(), addedOperation.getVisibility(), removedOperation, addedOperation);
			refactorings.add(refactoring);
		}
		if(finalChanged) {
			if(addedOperation.isFinal()) {
				AddMethodModifierRefactoring refactoring = new AddMethodModifierRefactoring("final", removedOperation, addedOperation);
				refactorings.add(refactoring);
			}
			else if(removedOperation.isFinal()) {
				RemoveMethodModifierRefactoring refactoring = new RemoveMethodModifierRefactoring("final", removedOperation, addedOperation);
				refactorings.add(refactoring);
			}
		}
		if(abstractionChanged) {
			if(addedOperation.isAbstract()) {
				AddMethodModifierRefactoring refactoring = new AddMethodModifierRefactoring("abstract", removedOperation, addedOperation);
				refactorings.add(refactoring);
			}
			else if(removedOperation.isAbstract()) {
				RemoveMethodModifierRefactoring refactoring = new RemoveMethodModifierRefactoring("abstract", removedOperation, addedOperation);
				refactorings.add(refactoring);
			}
		}
		if(staticChanged) {
			if(addedOperation.isStatic()) {
				AddMethodModifierRefactoring refactoring = new AddMethodModifierRefactoring("static", removedOperation, addedOperation);
				refactorings.add(refactoring);
			}
			else if(removedOperation.isStatic()) {
				RemoveMethodModifierRefactoring refactoring = new RemoveMethodModifierRefactoring("static", removedOperation, addedOperation);
				refactorings.add(refactoring);
			}
		}
		if(synchronizedChanged) {
			if(addedOperation.isSynchronized()) {
				AddMethodModifierRefactoring refactoring = new AddMethodModifierRefactoring("synchronized", removedOperation, addedOperation);
				refactorings.add(refactoring);
			}
			else if(removedOperation.isSynchronized()) {
				RemoveMethodModifierRefactoring refactoring = new RemoveMethodModifierRefactoring("synchronized", removedOperation, addedOperation);
				refactorings.add(refactoring);
			}
		}
		return refactorings;
	}
}
