/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */

package com.neosyn.cg;

import static com.google.inject.name.Names.named;
import static org.eclipse.xtext.scoping.impl.AbstractDeclarativeScopeProvider.NAMED_DELEGATE;

import org.eclipse.xtext.conversion.IValueConverterService;
import org.eclipse.xtext.debug.IStratumBreakpointSupport;
import org.eclipse.xtext.formatting.IWhitespaceInformationProvider;
import org.eclipse.xtext.generator.IGenerator;
import org.eclipse.xtext.linking.ILinker;
import org.eclipse.xtext.linking.ILinkingDiagnosticMessageProvider;
import org.eclipse.xtext.linking.ILinkingService;
import org.eclipse.xtext.linking.impl.ImportedNamesAdapter;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.parser.antlr.ISyntaxErrorMessageProvider;
import org.eclipse.xtext.resource.IDefaultResourceDescriptionStrategy;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.scoping.IGlobalScopeProvider;
import org.eclipse.xtext.scoping.IScopeProvider;
import org.eclipse.xtext.serializer.tokens.SerializerScopeProviderBinding;

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.neosyn.cg.conversion.CgValueConverter;
import com.neosyn.cg.debug.CgStratumBreakpointSupport;
import com.neosyn.cg.formatting2.WhitespaceInfoProvider;
import com.neosyn.cg.generator.CgGenerator;
import com.neosyn.cg.internal.validation.CgSyntaxErrorMessageProvider;
import com.neosyn.cg.linking.CgLinker;
import com.neosyn.cg.linking.CgLinkingDiagnosticMessageProvider;
import com.neosyn.cg.linking.CgLinkingService;
import com.neosyn.cg.resource.CgResourceDescriptionManager;
import com.neosyn.cg.resource.CgResourceStrategy;
import com.neosyn.cg.scoping.CgGlobalScopeProvider;
import com.neosyn.cg.scoping.CgImportedNamesAdapterProvider;
import com.neosyn.cg.scoping.CgImportedNamespaceScopeProvider;
import com.neosyn.cg.scoping.CgSerializerScopeProvider;
import com.neosyn.cg.services.CgQualifiedNameProvider;

/**
 * Use this class to register components to be used at runtime / without the Equinox extension
 * registry.
 */
@SuppressWarnings("restriction")
public class CgRuntimeModule extends AbstractCgRuntimeModule {

	public Class<? extends IDefaultResourceDescriptionStrategy> bindIDefaultResourceDescriptionStrategy() {
		return CgResourceStrategy.class;
	}

	public Class<? extends IGenerator> bindIGenerator() {
		return CgGenerator.class;
	}

	@Override
	public Class<? extends IGlobalScopeProvider> bindIGlobalScopeProvider() {
		return CgGlobalScopeProvider.class;
	}

	@Override
	public Class<? extends ILinker> bindILinker() {
		return CgLinker.class;
	}

	public Class<? extends ILinkingDiagnosticMessageProvider> bindILinkingDiagnosticMessageProvider() {
		return CgLinkingDiagnosticMessageProvider.class;
	}

	@Override
	public Class<? extends ILinkingService> bindILinkingService() {
		return CgLinkingService.class;
	}

	@Override
	public Class<? extends IQualifiedNameProvider> bindIQualifiedNameProvider() {
		return CgQualifiedNameProvider.class;
	}

	public Class<? extends IResourceDescription.Manager> bindIResourceDescription$Manager() {
		return CgResourceDescriptionManager.class;
	}

	public Class<? extends IStratumBreakpointSupport> bindIStratumBreakpointSupport() {
		return CgStratumBreakpointSupport.class;
	}

	public Class<? extends ISyntaxErrorMessageProvider> bindISyntaxErrorMessageProvider() {
		return CgSyntaxErrorMessageProvider.class;
	}

	@Override
	public Class<? extends IValueConverterService> bindIValueConverterService() {
		return CgValueConverter.class;
	}

	public Class<? extends IWhitespaceInformationProvider> bindIWhitespaceInformationProvider() {
		return WhitespaceInfoProvider.class;
	}

	@Override
	public void configureIScopeProviderDelegate(Binder binder) {
		binder.bind(IScopeProvider.class).annotatedWith(named(NAMED_DELEGATE))
				.to(CgImportedNamespaceScopeProvider.class);
	}

	@Override
	public void configureSerializerIScopeProvider(Binder binder) {
		binder.bind(IScopeProvider.class).annotatedWith(SerializerScopeProviderBinding.class)
				.to(CgSerializerScopeProvider.class);
	}

	public Provider<? extends ImportedNamesAdapter> provideImportedNamesAdapter() {
		return new CgImportedNamesAdapterProvider();
	}

}
