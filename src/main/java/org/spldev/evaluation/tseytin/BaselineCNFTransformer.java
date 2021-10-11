/* -----------------------------------------------------------------------------
 * Formula Lib - Library to represent and edit propositional formulas.
 * Copyright (C) 2021  Sebastian Krieter
 *
 * This file is part of Formula Lib.
 *
 * Formula Lib is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * Formula Lib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Formula Lib.  If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/skrieter/formula> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.tseytin;

import de.ovgu.featureide.fm.core.init.FMCoreLibrary;
import de.ovgu.featureide.fm.core.init.LibraryManager;
import org.prop4j.Node;
import org.prop4j.Or;
import org.spldev.formula.expression.Expression;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.atomic.literal.VariableMap;
import org.spldev.formula.expression.compound.And;
import org.spldev.formula.expression.io.parse.NodeReader;
import org.spldev.formula.expression.io.parse.NodeWriter;
import org.spldev.formula.expression.transform.DistributiveLawTransformer.TransformException;
import org.spldev.formula.expression.transform.NormalForms;
import org.spldev.formula.expression.transform.NormalForms.NormalForm;
import org.spldev.formula.solver.javasmt.CNFTseytinTransformer;
import org.spldev.util.Result;
import org.spldev.util.job.Executor;
import org.spldev.util.job.InternalMonitor;
import org.spldev.util.job.NullMonitor;
import org.spldev.util.logging.Logger;
import org.spldev.util.tree.Trees;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class BaselineCNFTransformer extends CountingCNFTseytinTransformer {
	VariableMap variableMap;
	NodeWriter spldevNodeWriter = new NodeWriter();
	Function<Node, org.prop4j.NodeWriter> featureideNodeWriter = org.prop4j.NodeWriter::new;
	NodeReader spldevNodeReader = new NodeReader();
	org.prop4j.NodeReader featureideNodeReader = new org.prop4j.NodeReader();

	{
		LibraryManager.registerLibrary(FMCoreLibrary.getInstance());
		featureideNodeReader.activateShortSymbols();
	}

	@Override
	public Formula execute(Formula orgFormula, InternalMonitor monitor) {
		final List<Formula> clauses = new ArrayList<>();
		variableMap = VariableMap.fromExpression(orgFormula).clone();
		Formula formula = NormalForms.simplifyForNF(Trees.cloneTree(orgFormula));
		if (!(formula instanceof And)) {
			throw new RuntimeException("the given formula should be in proto-CNF!");
		}
		final List<Formula> children = ((And) formula).getChildren();
		children.forEach(child -> clauses.addAll(transform(child).getChildren()));
		formula = new And(clauses);
		formula = NormalForms.toClausalNF(formula, NormalForm.CNF);
		formula.setVariableMap(variableMap);
		return formula;
	}

	private And transform(Formula child) {
		final Formula clonedChild = Trees.cloneTree(child);
		try {
			return distributive(clonedChild, new NullMonitor());
		} catch (final TransformException | Or.TransformException e) {
			return tseytin(clonedChild);
		}
	}

	protected And distributive(Formula child, InternalMonitor monitor) throws TransformException {
		String serializedFormula = spldevNodeWriter.write(child);
		Node node = featureideNodeReader.stringToNode(serializedFormula);
		if (node == null)
			throw new RuntimeException("constraint " + child + " could not be read by FeatureIDE");
		Or.maximumNumberOfLiterals = maximumNumberOfLiterals;
		Node cnf = node.toRegularCNF();
		serializedFormula = featureideNodeWriter.apply(cnf).nodeToString();
		if (serializedFormula == null)
			throw new RuntimeException("constraint " + child + " could not be written by FeatureIDE");
		Result<Formula> protoCnfFormula = spldevNodeReader.read(serializedFormula);
		if (!protoCnfFormula.isPresent())
			throw new RuntimeException("constraint " + child + " could not be read by spldev");
		Formula cnfFormula = NormalForms.toClausalNF(NormalForms.simplifyForNF(protoCnfFormula.get()), NormalForm.CNF);
		return (And) cnfFormula;
	}

	protected And tseytin(Formula child) {
		And and = (And) Executor.run(new CNFTseytinTransformer(), child).orElse(Logger::logProblems);
		numberOfTseytinTransformedClauses += and.getChildren().stream().map(Expression::getChildren).map(List::size)
			.reduce(0, Integer::sum);
		for (String name : and.getVariableMap().getNames()) {
			if (name.startsWith("k!")) {
				String newName = name + "_" +
					numberOfTseytinTransformedConstraints;
				and.getVariableMap().renameVariable(name, newName);
				variableMap.addBooleanVariable(newName);
				and.getVariableMap().getVariable(newName).get().adaptVariableMap(variableMap);
			}
		}
		numberOfTseytinTransformedConstraints++;
		return and;
	}
}
