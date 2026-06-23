/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */


package com.neosyn.neosynide.internal.generators.verilog

import com.neosyn.core.IPathResolver
import com.neosyn.core.IProperties
import com.neosyn.models.dpn.DpnPackage
import com.neosyn.models.dpn.Entity
import com.neosyn.models.dpn.Goto
import com.neosyn.models.ir.BlockBasic
import com.neosyn.models.ir.BlockIf
import com.neosyn.models.ir.BlockWhile
import com.neosyn.models.ir.ExprVar
import com.neosyn.models.ir.Expression
import com.neosyn.models.ir.InstAssign
import com.neosyn.models.ir.InstCall
import com.neosyn.models.ir.InstLoad
import com.neosyn.models.ir.InstReturn
import com.neosyn.models.ir.InstStore
import com.neosyn.models.ir.Instruction
import com.neosyn.models.ir.Procedure
import com.neosyn.models.ir.Type
import com.neosyn.models.ir.TypeArray
import com.neosyn.models.ir.TypeFloat
import com.neosyn.models.ir.TypeInt
import com.neosyn.models.ir.TypeString
import com.neosyn.models.ir.Var
import com.neosyn.models.ir.util.TypeUtil
import com.neosyn.models.util.EcoreHelper
import com.neosyn.neosynide.internal.generators.Namer
import java.util.ArrayList
import java.util.List
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtend2.lib.StringConcatenation

/**
 * This class prints Verilog code from IR.
 * 


 */
class VerilogIrPrinter extends VerilogExpressionPrinter {

	val IPathResolver pathResolver

	// Write-forwarding: tracks last non-blocking indexed store to a state array
	// so a subsequent load from the same array generates a forwarding mux
	var Var pendingStoreArray = null
	var CharSequence pendingStoreIndexExpr = null
	var CharSequence pendingStoreValueExpr = null

	new(Namer namer, IPathResolver pathResolver) {
		super(namer);
		this.pathResolver = pathResolver
	}

	def resetWriteForwarding() {
		pendingStoreArray = null
		pendingStoreIndexExpr = null
		pendingStoreValueExpr = null
	}

	override caseBlockBasic(BlockBasic node)
		'''
		«doSwitch(node.instructions)»
		'''

	override caseBlockIf(BlockIf block)
		'''
		«printComments(block, block.lineNumber)»if («doSwitch(block.condition)») begin
		  «doSwitch(block.thenBlocks)»
		«IF block.elseRequired»
		end else begin
		  «doSwitch(block.elseBlocks)»
		«ENDIF»
		end
		«IF block.joinBlock !== null»
		«doSwitch(block.joinBlock)»
		«ENDIF»
		'''

	override caseBlockWhile(BlockWhile block)
		'''
		«printComments(block, block.lineNumber)»while («doSwitch(block.condition)») begin
		  «doSwitch(block.blocks)»
		  «doSwitch(block.joinBlock)»
		end
		'''

	override caseInstAssign(InstAssign assign) {
		'''
		«printComments(assign, assign.lineNumber)»«namer.getName(assign.target.variable)» = «doSwitch(assign.value)»;
		'''
	}

	override caseInstCall(InstCall call) {
		if (call.assert) {
			val expr = doSwitch(call.arguments.get(0))
			'''
			// synthesis translate_off
			if (~(«expr»)) begin
			  $display("Assertion failed: «expr»");
			  $stop;
			end
			// synthesis translate_on
			'''
		} else if (call.print) {
			'''
			// synthesis translate_off
			$display(«printDisplayParams(call.arguments)»);
			// synthesis translate_on
			'''
		} else {
			'''
			«IF call.target === null»
			«call.procedure.name»(«printCallParams(call.arguments)»);
			«ELSE»
			«namer.getName(call.target.variable)» = «call.procedure.name»(«printCallParams(call.arguments)»);
			«ENDIF»
			'''
		}
	}

	override caseInstReturn(InstReturn instReturn) {
		if (instReturn.value !== null) {
			val procedure = EcoreHelper.getContainerOfType(instReturn, Procedure)
			'''
			«procedure.name» = «doSwitch(instReturn.value)»;
			'''
		}
	}
	
	override caseInstLoad(InstLoad load) {
		var target = load.target.variable
		val source = load.source.variable
		if (target.type.array) {
			pendingStoreArray = null
			copyArray(target, load.indexes.get(0), source)
		} else if (pendingStoreArray !== null && source === pendingStoreArray && !load.indexes.empty) {
			// Write-forwarding: the array was just stored with non-blocking <=
			// Generate forwarding mux to preserve write-first semantics
			val loadIndexExpr = doSwitch(load.indexes.get(0))
			val storeIdx = pendingStoreIndexExpr
			val storeVal = pendingStoreValueExpr
			val sourceName = namer.getName(source)
			val targetName = namer.getName(target)
			pendingStoreArray = null
			'''
			«printComments(load, load.lineNumber)»if («storeIdx» == «loadIndexExpr»)
			  «targetName» = «storeVal»;
			else
			  «targetName» = «sourceName»[«loadIndexExpr»];
			'''
		} else {
			pendingStoreArray = null
			'''
			«printComments(load, load.lineNumber)»«namer.getName(target)» = «namer.getName(source)»«printIndexes(source.type, load.indexes)»;
			'''
		}
	}

	override caseInstruction(Instruction instr) {
		if (instr instanceof Goto) {
			// Skip gotos with null targets (can occur in incomplete FSMs)
			if (instr.target === null) {
				return ''''''
			}
			'''FSM <= «instr.target.name»;'''
		}
	}

	override caseInstStore(InstStore store) {
		val target = store.target.variable
		val value = store.value
		if (value instanceof ExprVar) {
			val source = value.use.variable
			if (source.type.array) {
				return copyArray(target, store.indexes.get(0), source)
			}
		}

		val procedure = EcoreHelper.getContainerOfType(store, typeof(Procedure))
		val containingFeature = procedure.eContainingFeature
		val combinational = containingFeature == DpnPackage.Literals.ACTION__COMBINATIONAL
		val scheduler = containingFeature == DpnPackage.Literals.ACTION__SCHEDULER
		val blocking = target.local || combinational || scheduler

		// Track non-blocking indexed store to state array for write-forwarding.
		// When a load from the same array follows, we generate a forwarding mux
		// to preserve write-first semantics (e.g., register file read-after-write).
		if (!blocking && target.type.array && !store.indexes.empty) {
			pendingStoreArray = target
			pendingStoreIndexExpr = doSwitch(store.indexes.get(0))
			pendingStoreValueExpr = doSwitch(value)
		} else {
			pendingStoreArray = null
		}

		'''
		«printComments(store, store.lineNumber)»«namer.getName(target)»«printIndexes(target.type, store.indexes)» «IF !blocking»<«ENDIF»= «doSwitch(value)»;
		'''
	}

	override caseTypeArray(TypeArray type) {
		var depth = 1
		for (dim : type.dimensions ) {
			depth = depth * dim
		}
		depth = depth - 1
		'''[0 : «depth»]'''
	}

	override caseTypeFloat(TypeFloat type) '''real'''

	override caseTypeInt(TypeInt type) {
		// Guard against unresolved sizes (can happen with built-in entity ports)
		val size = if (type.size <= 0) 1 else type.size
		// if signed, prints 'signed [size - 1 : 0]', otherwise just '[size - 1 : 0]'
		'''«IF type.signed»signed «ENDIF»[«size - 1» : 0]'''
	}

	override caseTypeString(TypeString type) {
		throw new IllegalArgumentException("unsupported String type")
	}

	override caseVar(Var variable) {
		val name = namer.getName(variable)
		if (variable.type.array) {
			'''«doSwitch((variable.type as TypeArray).elementType)» «name» «doSwitch(variable.type)»'''
		} else {
			'''«doSwitch(variable.type)» «name»'''
		}
	}

	def private copyArray(Var target, Expression index, Var source) {
		var loopVar = (index as ExprVar).use.variable.name
		val bound = (source.type as TypeArray).dimensions.fold(1, [acc, elt|acc * elt])

		'''
		for («loopVar» = 0; «loopVar» < «bound»; «loopVar» = «loopVar» + 1) begin
		  «target.name»[«loopVar»] «IF target.isGlobal»<«ENDIF»= «source.name»[«loopVar»];
		end
		'''
	}

	def doSwitch(List<? extends EObject> objects)
		'''
		«FOR eObject : objects»
			«doSwitch(eObject)»
		«ENDFOR»
		'''
	
	def private getAbsoluteHexPath(Entity entity, String varName) {
		val path = pathResolver.getFullPath(entity)
		path.substring(0, path.lastIndexOf('.')) + "_" + varName + ".hex"
	}

	def private printCallParams(List<Expression> arguments) {
		if (arguments.empty) {
			"1'b1"
		} else {
			'''«FOR expr : arguments SEPARATOR ", "»«doSwitch(expr)»«ENDFOR»'''
		}
	}

	def printComments(EObject obj, int lineNumber) {
		val entity = EcoreHelper.getContainerOfType(obj, Entity)
		val comments = entity.properties.getAsJsonObject(IProperties.PROP_COMMENTS)
		if (comments !== null) {
			val lines = comments.getAsJsonArray(String.valueOf(lineNumber))
			if (lines !== null && lines.size != 0) {
				'''
				«FOR line : lines»
				// «line.asString»
				«ENDFOR»
				'''
			}
		}
	}

	def private printDisplayParams(List<Expression> arguments) {
		val format = new StringConcatenation
		val args = new ArrayList<CharSequence>
		format.append("\"")
		args.add(format)
		for (expr : arguments) {
			if (expr.exprString) {
				format.append(doSwitch(expr))
			} else {
				format.append("%0h")
				args.add(doSwitch(expr))
			}
		}

		format.append("\"")
		args.join(", ")
	}
	
	def printFunction(Procedure procedure) {
		'''	
		function «doSwitch(procedure.returnType)» «procedure.name»(«IF procedure.parameters.empty»input _dummy«ELSE»«FOR param : procedure.parameters SEPARATOR ', '»input «doSwitch(param)»«ENDFOR»«ENDIF»);
		  «FOR local : procedure.locals»
		  reg «doSwitch(local)»;
		  «ENDFOR»
		  begin
		    «doSwitch(procedure.blocks)»
		  end
		endfunction

		'''
	}

	def private printIndexes(Type type, List<Expression> indexes) {
		val it = indexes.iterator
		if (it.hasNext) {
			val firstIndex = it.next
			if (it.hasNext) {
				'''[{«indexes.map[index|doSwitch(index)].join(', ')»}]'''
			} else {
				'''[«doSwitch(firstIndex)»]'''
			}
		}
	}

	/**
	 * prints the initial value of the given variable. If the variable has no
	 * initial value, the value that corresponds to the neutral element of
	 * the variable's type is returned.
	 * 
	 * variable is never an array.
	 */
	def printInitialValue(Var variable) {
		val type = variable.type
		val value = variable.initialValue
		if (value === null) {
			'''«TypeUtil.getSize(type)»'b0'''
		} else {
			doSwitch(value)
		}
	}

	def printStateVar(Entity entity, Var variable) {
		val name = namer.getName(variable)

		'''
		«IF variable.assignable || variable.type.array»
			reg «doSwitch(variable)»;
			«IF variable.type.array»
				initial $readmemh("«getAbsoluteHexPath(entity, name)»", «name»);
			«ENDIF»
		«ELSE»
			localparam «doSwitch(variable)» = «printInitialValue(variable)»;
		«ENDIF»
		'''
	}

}
