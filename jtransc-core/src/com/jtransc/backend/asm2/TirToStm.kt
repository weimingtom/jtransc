package com.jtransc.backend.asm2

import com.jtransc.ast.*
import com.jtransc.ds.cast
import com.jtransc.org.objectweb.asm.Label

class TirToStm(val methodType: AstType.METHOD, val blockContext: BlockContext, val types: AstTypes) {
	val locals = hashMapOf<Local, AstLocal>()
	val stms = arrayListOf<AstStm>()
	var id = 0

	fun AstType.convertType(): AstType {
		val type = this
		return when (type) {
			is AstType.COMMON -> {
				if (type.single != null) {
					type.single!!.convertType()
				} else {
					if (type.elements.any { it is AstType.Primitive }) {
						getCommonTypePrim(type.elements.cast<AstType.Primitive>())
					} else {
						AstType.OBJECT
					}
				}
			}
			else -> type
		}
	}

	val Local.ast: AstLocal get() {
		val canonicalLocal = Local(this.type.convertType(), this.index)
		if (canonicalLocal.type is AstType.UNKNOWN) {
			println("ASSERT UNKNOWN!: $canonicalLocal")
		}
		return locals.getOrPut(canonicalLocal) { AstLocal(id++, canonicalLocal.type) }
	}
	val Label.ast: AstLabel get() = blockContext.label(this)

	val Local.expr: AstExpr.LOCAL get() = AstExpr.LOCAL(this.ast)

	val Operand.expr: AstExpr get() = when (this) {
		is Constant -> AstExpr.LITERAL(this.v)
		is Param -> AstExpr.PARAM(AstArgument(this.index, this.type.convertType()))
		is Local -> AstExpr.LOCAL(this.ast)
		is This -> AstExpr.THIS(this.clazz.name)
	//is CatchException -> AstExpr.CAUGHT_EXCEPTION(this.type)
		is CatchException -> AstExpr.CAUGHT_EXCEPTION(AstType.OBJECT)
		else -> TODO("$this")
	}

	fun convert(tirs: List<TIR>) {
		for (tir in tirs) {
			when (tir) {
				is TIR.NOP -> Unit
				is TIR.MOV -> stms += AstStm.SET_LOCAL(tir.dst.expr, tir.src.expr.castTo(tir.dst.type))
				is TIR.INSTANCEOF -> stms += AstStm.SET_LOCAL(tir.dst.expr, AstExpr.INSTANCE_OF(tir.src.expr, tir.type as AstType.Reference))
				is TIR.CONV -> stms += AstStm.SET_LOCAL(tir.dst.expr, AstExpr.CAST(tir.src.expr, tir.dst.type))
				is TIR.ARRAYLENGTH -> stms += AstStm.SET_LOCAL(tir.dst.expr, AstExpr.ARRAY_LENGTH(tir.obj.expr))
				is TIR.NEW -> stms += AstStm.SET_LOCAL(tir.dst.expr, AstExpr.NEW(tir.type))
				is TIR.NEWARRAY -> stms += AstStm.SET_LOCAL(tir.dst.expr, AstExpr.NEW_ARRAY(tir.arrayType, tir.lens.map { it.expr }))
				is TIR.UNOP -> stms += AstStm.SET_LOCAL(tir.dst.expr, AstExpr.UNOP(tir.op, tir.r.expr))
				is TIR.BINOP -> stms += AstStm.SET_LOCAL(tir.dst.expr, AstExpr.BINOP(tir.dst.type, tir.l.expr, tir.op, tir.r.expr))
				is TIR.ARRAY_STORE -> {
					stms += AstStm.SET_ARRAY(tir.array.expr, tir.index.expr, tir.value.expr.castTo(tir.elementType.convertType()))
				}
				is TIR.ARRAY_LOAD -> {
					stms += AstStm.SET_LOCAL(tir.dst.expr, AstExpr.ARRAY_ACCESS(tir.array.expr, tir.index.expr))
				}
				is TIR.GETSTATIC -> stms += AstStm.SET_LOCAL(tir.dst.expr, AstExpr.FIELD_STATIC_ACCESS(tir.field))
				is TIR.GETFIELD -> stms += AstStm.SET_LOCAL(tir.dst.expr, AstExpr.FIELD_INSTANCE_ACCESS(tir.field, tir.obj.expr.castTo(tir.field.containingTypeRef)))
				is TIR.PUTSTATIC -> stms += AstStm.SET_FIELD_STATIC(tir.field, tir.src.expr.castTo(tir.field.type))
				is TIR.PUTFIELD -> stms += AstStm.SET_FIELD_INSTANCE(tir.field, tir.obj.expr.castTo(tir.field.containingTypeRef), tir.src.expr.castTo(tir.field.type))
				is TIR.INVOKE_COMMON -> {
					val method = tir.method
					val args = tir.args.zip(method.type.args).map { it.first.expr.castTo(it.second.type) }
					val expr = if (tir.obj != null) {
						AstExpr.CALL_INSTANCE(tir.obj!!.expr.castTo(tir.method.containingClassType), tir.method, args, isSpecial = tir.isSpecial)
					} else {
						AstExpr.CALL_STATIC(tir.method, args, isSpecial = tir.isSpecial)
					}
					if (tir is TIR.INVOKE) {
						stms += AstStm.SET_LOCAL(tir.dst.expr, expr)
					} else {
						stms += AstStm.STM_EXPR(expr)
					}
				}
				is TIR.MONITOR -> stms += if (tir.enter) AstStm.MONITOR_ENTER(tir.obj.expr) else AstStm.MONITOR_EXIT(tir.obj.expr)
			// control flow:
				is TIR.LABEL -> stms += AstStm.STM_LABEL(tir.label.ast)
				is TIR.JUMP -> stms += AstStm.GOTO(tir.label.ast)
				is TIR.JUMP_IF -> stms += AstStm.IF_GOTO(tir.label.ast, AstExpr.BINOP(AstType.BOOL, tir.l.expr, tir.op, tir.r.expr))
				is TIR.SWITCH_GOTO -> stms += AstStm.SWITCH_GOTO(tir.subject.expr, tir.deflt.ast, tir.cases.map { it.key to it.value.ast })
				is TIR.RET -> {
					//if (methodType.ret == AstType.REF("j.ClassInfo")) {
					//	println("go!")
					//}
					stms += if (tir.v != null) AstStm.RETURN(tir.v.expr.castTo(methodType.ret)) else AstStm.RETURN_VOID()
				}
				is TIR.THROW -> stms += AstStm.THROW(tir.ex.expr)
			//is TIR.PHI_PLACEHOLDER -> stms += AstStm.NOP("PHI_PLACEHOLDER")
				else -> TODO("$tir")
			}
		}
	}
}
