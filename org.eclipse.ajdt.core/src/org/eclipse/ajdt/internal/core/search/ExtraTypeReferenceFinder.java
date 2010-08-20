/*******************************************************************************
 * Copyright (c) 2010 SpringSource and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.core.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.aspectj.asm.IHierarchy;
import org.aspectj.asm.IProgramElement;
import org.aspectj.asm.IProgramElement.Kind;
import org.aspectj.org.eclipse.jdt.core.dom.AjASTVisitor;
import org.aspectj.org.eclipse.jdt.core.dom.BodyDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.DeclareAnnotationDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.DefaultTypePattern;
import org.aspectj.org.eclipse.jdt.core.dom.PatternNode;
import org.aspectj.org.eclipse.jdt.core.dom.SignaturePattern;
import org.aspectj.org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.core.javaelements.DeclareElement;
import org.eclipse.ajdt.core.javaelements.IAspectJElement;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.ajdt.core.javaelements.PointcutUtilities;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.ajdt.core.model.AJRelationshipType;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeReferenceMatch;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.core.hierarchy.HierarchyResolver;
import org.eclipse.jdt.internal.core.search.matching.PossibleMatch;
import org.eclipse.jdt.internal.core.search.matching.TypeReferencePattern;

/**
 * Helps find Type references of a {@link SearchPattern} inside of an {@link AspectElement}
 * 
 * @author Andrew Eisenberg
 * @created Aug 6, 2010
 */
public class ExtraTypeReferenceFinder implements IExtraMatchFinder<TypeReferencePattern> {

    public List<SearchMatch> findExtraMatches(PossibleMatch match,
            TypeReferencePattern pattern, HierarchyResolver resolver)
            throws JavaModelException {
        if (! (match.openable instanceof AJCompilationUnit)) {
            return Collections.emptyList();
        }
        
        AJCompilationUnit unit = (AJCompilationUnit) match.openable;

        // first look for matches inside of ITDs (ie- the target type)
        List<IAspectJElement> allItds = getRelevantChildren(unit);
        
        List<SearchMatch> matches = new ArrayList<SearchMatch>(allItds.size());
        // check each ITD's target type to see if it is a match
        for (IAspectJElement elt : allItds) {
            if (elt instanceof IntertypeElement) {
                IntertypeElement itd = (IntertypeElement) elt; 
                if (isMatch(itd, pattern)) {
                    // convert to a match
                    matches.add(createMatch(itd, match.document.getParticipant()));
                }
            } else if (elt instanceof DeclareElement) {
                DeclareElement decl = (DeclareElement) elt; 
                matches.addAll(findMatches(decl, match.document.getParticipant(), pattern));
            }
        }
        return matches;
    }

    private List<SearchMatch> findMatches(DeclareElement decl, SearchParticipant participant, TypeReferencePattern pattern) throws JavaModelException {
        AJCompilationUnit unit = ((AJCompilationUnit) decl.getCompilationUnit());
        char[] contents = null;
        try { 
            unit.requestOriginalContentMode();
            contents = unit.getContents();
        } finally {
            unit.discardOriginalContentMode();
        }
        if (contents != null) {
            ISourceRange sourceRange = decl.getSourceRange();
            BodyDeclaration bdecl = PointcutUtilities.createSingleBodyDeclarationNode(sourceRange.getOffset(), sourceRange.getOffset() + sourceRange.getLength(), contents);
            // found it!
            if (bdecl != null) {
                DeclareVisitor visitor = new DeclareVisitor(participant, String.valueOf(TargetTypeUtils.getSimpleName(pattern)), sourceRange.getOffset(), decl, contents);
                bdecl.accept(visitor);
                return visitor.getAccumulatedMatches();
            }
        }
        return Collections.emptyList();
    }

    /**
     * @param decl
     * @param pattern
     * @throws JavaModelException
     */
    private boolean maybeHasTypeMatches(DeclareElement decl,
            TypeReferencePattern pattern) throws JavaModelException {
        AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(decl);
        if (! model.hasModel()) {
            return false;
        }
        String searchType = String.valueOf(TargetTypeUtils.getName(pattern));
        boolean doIt = false;
        IProgramElement ipe = model.javaElementToProgramElement(decl);
        if (ipe != IHierarchy.NO_STRUCTURE) {
            List<String> typeNames = ipe.getParentTypes();
            if (typeNames != null) {
                for (String typeName : typeNames) {
                    if (searchType.equals(typeName)) {
                        doIt = true;
                        break;
                    }
                }
            }
            if (! doIt) {
                doIt = searchType.equals(ipe.getAnnotationType());
            }
        }
        if (!doIt) {
            // now look at the target type
            // not exactly right since the actual pointcut may not be an explicit type, but rather an annotation
            doIt = seearchRelationships(decl, searchType, model, AJRelationshipManager.DECLARED_ON);
            if (!doIt) {
                doIt = seearchRelationships(decl, searchType, model, AJRelationshipManager.ANNOTATES);
            }
        }
        return doIt;
    }
    
    private boolean seearchRelationships(DeclareElement decl,
            String searchTypeStr, AJProjectModelFacade model,
            AJRelationshipType relType) {
        boolean doIt = false;
        List<IJavaElement> rels = model.getRelationshipsForElement(decl, relType);
        for (IJavaElement rel : rels) {
            IType type;
            if (rel.getElementType() == IJavaElement.TYPE) {
                type = (IType) rel;
            } else {
                type = (IType) rel.getAncestor(IJavaElement.TYPE);
            }
            if (type != null && type.getFullyQualifiedName('.').equals(searchTypeStr)) {
                doIt = true;
                break;
            }
        }
        return doIt;
    }

    private SearchMatch createMatch(IntertypeElement itd, SearchParticipant participant) throws JavaModelException {
        String itdName = itd.getElementName();
        // might find a fully qualified name
        int typeNameEnd = Math.max(itdName.lastIndexOf('.'), 0);
        String typeName = itdName.substring(0, typeNameEnd);
        
        // itd.getNameRange() returns the range of the name, but excludes the target type.  Must subtract from there.  Make assumption that there are no spaces
        // or comments between '.' and the rest of the name
        int sourceStart;
        if (itd.getAJKind() == Kind.INTER_TYPE_CONSTRUCTOR) {
            sourceStart = itd.getNameRange().getOffset();
        } else {
            sourceStart = itd.getNameRange().getOffset() - 1 - typeName.length();
        }
        return new TypeReferenceMatch(itd, SearchMatch.A_ACCURATE, sourceStart, typeName.length(), false, participant, itd.getResource());
    }

    private boolean isMatch(IntertypeElement itd, TypeReferencePattern pattern) throws JavaModelException {
        char[] targetTypeName = TargetTypeUtils.getName(pattern);
        IType targetType = itd.findTargetType();
        if (targetType != null) {
            return CharOperation.equals(targetTypeName, targetType.getFullyQualifiedName().toCharArray());
        } else {
            return false;
        }
    }

    private List<IAspectJElement> getRelevantChildren(IParent parent) throws JavaModelException {
        IJavaElement[] children = parent.getChildren();
        List<IAspectJElement> allItds = new LinkedList<IAspectJElement>();
        
        for (IJavaElement elt : children) {
            if (elt instanceof IntertypeElement || elt instanceof DeclareElement) {
                allItds.add((IAspectJElement) elt);
            } else if (elt.getElementType() == IJavaElement.TYPE) {
                allItds.addAll(getRelevantChildren((IParent) elt));
            }
        }
        return allItds;
    }
    
    class DeclareVisitor extends AjASTVisitor {
        private final String searchTypeSimpleName;
        private final String dotSearchTypeSimpleName;
        private final String atSearchTypeSimpleName;
        private final SearchParticipant participant;
        private final int offset;
        private final DeclareElement decl;
        private final List<SearchMatch> accumulatedMatches;
        private final char[] fileContents;
        public DeclareVisitor(SearchParticipant participant, String searchTypeSimpleName,
                int offset, DeclareElement decl, char[] fileContents) {
            this.participant = participant;
            this.searchTypeSimpleName = searchTypeSimpleName;
            this.dotSearchTypeSimpleName = "." + searchTypeSimpleName;
            this.atSearchTypeSimpleName = "@" + searchTypeSimpleName;
            this.offset = offset;
            this.accumulatedMatches = new ArrayList<SearchMatch>();
            this.decl = decl;
            this.fileContents = fileContents;
        }

        List<SearchMatch> getAccumulatedMatches() {
            return accumulatedMatches;
        }
        
        @Override
        public boolean visit(DefaultTypePattern node) {
            // We have already checked qualified names at this point, 
            // so we can assume a match if the simple names match.
            String detail = node.getDetail();
            if (detail != null) {
                if (searchTypeSimpleName.equals(detail) || 
                        detail.endsWith(dotSearchTypeSimpleName)) {
                    // need to match only the simple part of the name
                    int end = node.getStartPosition() + node.getLength();
                    int start = end - searchTypeSimpleName.length();
                    int actualStart = start + offset;
                    accumulatedMatches.add(new TypeReferenceMatch(decl, SearchMatch.A_ACCURATE, 
                            actualStart, searchTypeSimpleName.length(), false, participant, decl.getResource()));
                } else if (isComplexTypePattern(detail)) {
                    // must do something more complex
                    findMatchesInComplexPattern(node);
                }
            }
            return super.visit(node);
        }

        /**
         * A type pattern is complex if it is more than just a type name
         * It will have more than just Java identifier characters
         * @param detail
         * @return
         */
        private boolean isComplexTypePattern(String detail) {
            return !JavaConventions.validateJavaTypeName(detail, CompilerOptions.VERSION_1_3,CompilerOptions.VERSION_1_3).isOK();
        }

        @Override
        public boolean visit(SignaturePattern node) {
            findMatchesInComplexPattern(node);
            return super.visit(node);
        }

        /**
         * Looks for matches in a complex AJ pattern.  Since we don't have bindings here,
         * only look for simple names.  All matches marked as inaccurate
         * @param node
         */
        private void findMatchesInComplexPattern(PatternNode node) {
            Map<String, List<Integer>> matches = PointcutUtilities.findAllIdentifiers(getActualText(node));
            List<Integer> matchLocs = matches.get(searchTypeSimpleName);
            if (matchLocs != null) {
                for (Integer matchLoc : matchLocs) {
                    // need to match only the simple part of the name
                    int start = matchLoc + // end of the name relative to the SignaturePattern's detail
                            node.getStartPosition() + // start of SignaturePattern relative to the declareDeclaration's start 
                            offset; // the offset of the declareDeclaration from the start of the file
                    accumulatedMatches.add(new TypeReferenceMatch(decl, SearchMatch.A_INACCURATE, 
                            start, searchTypeSimpleName.length(), false, participant, decl.getResource()));
                }
            }
        }

        /**
         * Can't use node.getDetail() here because that does not contain exactly what the underlying text is
         * @param node
         * @return
         */
        private String getActualText(PatternNode node) {
            return String.valueOf(CharOperation.subarray(fileContents, offset + node.getStartPosition(), offset + node.getStartPosition() + node.getLength()));
        }
        
        @Override
        public boolean visit(SimpleName node) {
            if (node.getParent() instanceof DeclareAnnotationDeclaration) {
                String name = node.toString();
                if (name.equals(atSearchTypeSimpleName) || name.endsWith(dotSearchTypeSimpleName)) {
                    // match found, now search for location
                    // +1 because the length is off by one...missing the '@'
                    int end = node.getStartPosition() + node.getLength() + 1;
                    int start = end - searchTypeSimpleName.length();
                    int actualStart = start + offset;
                    accumulatedMatches.add(new TypeReferenceMatch(decl, SearchMatch.A_ACCURATE, 
                            actualStart, searchTypeSimpleName.length(), false, participant, decl.getResource()));
                }
            }
            return super.visit(node);
        }
        
    }
}