/* -----------------------------------------------------------------------------
 * Evaluation-Tseytin - Program for the evaluation of the Tseytin transformation.
 * Copyright (C) 2021  Sebastian Krieter, Elias Kuiter
 * 
 * This file is part of Evaluation-Tseytin.
 * 
 * Evaluation-Tseytin is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-Tseytin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-Tseytin.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/ekuiter/evaluation-tseytin> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.tseytin;

import de.ovgu.featureide.fm.core.PluginID;
import de.ovgu.featureide.fm.core.base.FeatureUtils;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.core.base.IFeatureStructure;
import de.ovgu.featureide.fm.core.io.AFeatureModelFormat;
import de.ovgu.featureide.fm.core.io.ProblemList;
import org.prop4j.And;
import org.prop4j.Node;
import org.prop4j.NodeReader;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class KConfigReaderFormat extends AFeatureModelFormat {
	static class KconfigNodeReader extends NodeReader {
		KconfigNodeReader() {
			try {
				Field field = NodeReader.class.getDeclaredField("symbols");
				field.setAccessible(true);
				field.set(this, new String[] { "==", "=>", "|", "&", "!" });
			} catch (NoSuchFieldException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}

	public static final String ID = PluginID.PLUGIN_ID + ".format.fm." + KConfigReaderFormat.class.getSimpleName();

	@Override
	public ProblemList read(IFeatureModel featureModel, CharSequence source) {
		setFactory(featureModel);

		final NodeReader nodeReader = new KconfigNodeReader();
		List<Node> constraints = source.toString().lines() //
			.map(String::trim) //
			.filter(l -> !l.isEmpty()) //
			.filter(l -> !l.startsWith("#")) //
			.filter(l -> !l.contains("=")) //
			.map(l -> l.replace("-", "_"))
			.map(l -> l.replaceAll("def\\((\\w+)\\)", "$1"))
			.map(nodeReader::stringToNode) //
			.filter(Objects::nonNull) // ignore non-Boolean constraints
			.collect(Collectors.toList());

		featureModel.reset();
		And andNode = new And(constraints);
		addNodeToFeatureModel(featureModel, andNode, andNode.getUniqueContainedFeatures());

		return new ProblemList();
	}

	/**
	 * Adds the given propositional node to the given feature model. The current
	 * implementation is naive in that it does not attempt to interpret any
	 * constraint as {@link IFeatureStructure structure}.
	 *
	 * @param featureModel feature model to edit
	 * @param node         propositional node to add
	 * @param variables    the variables of the propositional node
	 */
	private void addNodeToFeatureModel(IFeatureModel featureModel, Node node, Collection<String> variables) {
		// Add a feature for each variable.
		for (final String variable : variables) {
			final IFeature feature = factory.createFeature(featureModel, variable.toString());
			FeatureUtils.addFeature(featureModel, feature);
		}

		// Add a constraint for each conjunctive clause.
		final List<Node> clauses = node instanceof And ? Arrays.asList(node.getChildren())
			: Collections.singletonList(node);
		for (final Node clause : clauses) {
			FeatureUtils.addConstraint(featureModel, factory.createConstraint(featureModel, clause));
		}
	}

	@Override
	public String getSuffix() {
		return "model";
	}

	@Override
	public KConfigReaderFormat getInstance() {
		return this;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public boolean supportsRead() {
		return true;
	}

	@Override
	public String getName() {
		return "kconfigreader";
	}

}
