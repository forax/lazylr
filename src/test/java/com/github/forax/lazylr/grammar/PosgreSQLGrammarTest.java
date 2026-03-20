package com.github.forax.lazylr.grammar;

import com.github.forax.lazylr.Lexer;
import com.github.forax.lazylr.MetaGrammar;
import com.github.forax.lazylr.Parser;
import com.github.forax.lazylr.ParserListener;
import com.github.forax.lazylr.ParsingException;
import com.github.forax.lazylr.Production;
import com.github.forax.lazylr.Terminal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public final class PosgreSQLGrammarTest {

  // -------------------------------------------------------
  //  Grammar construction — package-visible so additional
  //  test files can share the same MetaGrammar instance.
  // -------------------------------------------------------
  static MetaGrammar create() {
    return MetaGrammar.load("""
        // ============================================================
        //  PostgreSQL SQL like Grammar — MetaGrammar format
        //
        // Loosely based on
        //  (https://github.com/postgres/postgres/blob/master/src/backend/parser/gram.y)
        // ============================================================

        tokens {
          // ----- Keywords -----
          kw_all:           /ALL/
          kw_and:           /AND/
          kw_any:           /ANY/
          kw_as:            /AS/
          kw_asc:           /ASC/
          kw_begin:         /BEGIN/
          kw_between:       /BETWEEN/
          kw_by:            /BY/
          kw_case:          /CASE/
          kw_cast:          /CAST/
          kw_collate:       /COLLATE/
          kw_column:        /COLUMN/
          kw_commit:        /COMMIT/
          kw_conflict:      /CONFLICT/
          kw_constraint:    /CONSTRAINT/
          kw_create:        /CREATE/
          kw_cross:         /CROSS/
          kw_current_date:  /CURRENT_DATE/
          kw_current_time:  /CURRENT_TIME/
          kw_current_timestamp: /CURRENT_TIMESTAMP/
          kw_default:       /DEFAULT/
          kw_delete:        /DELETE/
          kw_desc:          /DESC/
          kw_distinct:      /DISTINCT/
          kw_do:            /DO/
          kw_drop:          /DROP/
          kw_else:          /ELSE/
          kw_end:           /END/
          kw_escape:        /ESCAPE/
          kw_except:        /EXCEPT/
          kw_exists:        /EXISTS/
          kw_false:         /FALSE/
          kw_fetch:         /FETCH/
          kw_filter:        /FILTER/
          kw_foreign:       /FOREIGN/
          kw_from:          /FROM/
          kw_full:          /FULL/
          kw_group:         /GROUP/
          kw_having:        /HAVING/
          kw_if:            /IF/
          kw_ilike:         /ILIKE/
          kw_in:            /IN/
          kw_index:         /INDEX/
          kw_inner:         /INNER/
          kw_insert:        /INSERT/
          kw_intersect:     /INTERSECT/
          kw_into:          /INTO/
          kw_is:            /IS/
          kw_isnull:        /ISNULL/
          kw_join:          /JOIN/
          kw_key:           /KEY/
          kw_lateral:       /LATERAL/
          kw_left:          /LEFT/
          kw_like:          /LIKE/
          kw_limit:         /LIMIT/
          kw_matched:       /MATCHED/
          kw_merge:         /MERGE/
          kw_natural:       /NATURAL/
          kw_not:           /NOT/
          kw_notnull:       /NOTNULL/
          kw_null:          /NULL/
          kw_nulls:         /NULLS/
          kw_of:            /OF/
          kw_offset:        /OFFSET/
          kw_on:            /ON/
          kw_only:          /ONLY/
          kw_or:            /OR/
          kw_order:         /ORDER/
          kw_outer:         /OUTER/
          kw_over:          /OVER/
          kw_partition:     /PARTITION/
          kw_primary:       /PRIMARY/
          kw_references:    /REFERENCES/
          kw_release:       /RELEASE/
          kw_returning:     /RETURNING/
          kw_right:         /RIGHT/
          kw_rollback:      /ROLLBACK/
          kw_row:           /ROW/
          kw_rows:          /ROWS/
          kw_savepoint:     /SAVEPOINT/
          kw_select:        /SELECT/
          kw_sequence:      /SEQUENCE/
          kw_set:           /SET/
          kw_similar:       /SIMILAR/
          kw_some:          /SOME/
          kw_symmetric:     /SYMMETRIC/
          kw_table:         /TABLE/
          kw_then:          /THEN/
          kw_to:            /TO/
          kw_transaction:   /TRANSACTION/
          kw_true:          /TRUE/
          kw_union:         /UNION/
          kw_unique:        /UNIQUE/
          kw_unknown:       /UNKNOWN/
          kw_update:        /UPDATE/
          kw_using:         /USING/
          kw_value:         /VALUE/
          kw_values:        /VALUES/
          kw_view:          /VIEW/
          kw_when:          /WHEN/
          kw_where:         /WHERE/
          kw_window:        /WINDOW/
          kw_with:          /WITH/
          kw_within:        /WITHIN/

          // ----- Type keywords -----
          kw_bigint:        /BIGINT/
          kw_boolean:       /BOOLEAN/
          kw_char:          /CHAR/
          kw_character:     /CHARACTER/
          kw_date:          /DATE/
          kw_day:           /DAY/
          kw_decimal:       /DECIMAL/
          kw_double:        /DOUBLE/
          kw_float:         /FLOAT/
          kw_hour:          /HOUR/
          kw_int:           /INT/
          kw_integer:       /INTEGER/
          kw_interval:      /INTERVAL/
          kw_json:          /JSON/
          kw_jsonb:         /JSONB/
          kw_minute:        /MINUTE/
          kw_month:         /MONTH/
          kw_numeric:       /NUMERIC/
          kw_precision:     /PRECISION/
          kw_real:          /REAL/
          kw_second:        /SECOND/
          kw_smallint:      /SMALLINT/
          kw_text:          /TEXT/
          kw_time:          /TIME/
          kw_timestamp:     /TIMESTAMP/
          kw_timestamptz:   /TIMESTAMPTZ/
          kw_timetz:        /TIMETZ/
          kw_uuid:          /UUID/
          kw_varchar:       /VARCHAR/
          kw_varying:       /VARYING/
          kw_without:       /WITHOUT/
          kw_year:          /YEAR/
          kw_zone:          /ZONE/

          // ----- Constraint / table keywords -----
          kw_action:        /ACTION/
          kw_add:           /ADD/
          kw_alter:         /ALTER/
          kw_cascade:       /CASCADE/
          kw_check:         /CHECK/
          kw_cycle:         /CYCLE/
          kw_deferrable:    /DEFERRABLE/
          kw_deferred:      /DEFERRED/
          kw_inherits:      /INHERITS/
          kw_initially:     /INITIALLY/
          kw_immediate:     /IMMEDIATE/
          kw_match:         /MATCH/
          kw_no:            /NO/
          kw_nothing:       /NOTHING/
          kw_partial:       /PARTIAL/
          kw_rename:        /RENAME/
          kw_restrict:      /RESTRICT/
          kw_simple:        /SIMPLE/
          kw_work:          /WORK/

          // ----- Set-op / window keywords -----
          kw_cache:         /CACHE/
          kw_committed:     /COMMITTED/
          kw_current:       /CURRENT/
          kw_exclude:       /EXCLUDE/
          kw_first:         /FIRST/
          kw_following:     /FOLLOWING/
          kw_groups:        /GROUPS/
          kw_increment:     /INCREMENT/
          kw_isolation:     /ISOLATION/
          kw_last:          /LAST/
          kw_level:         /LEVEL/
          kw_maxvalue:      /MAXVALUE/
          kw_minvalue:      /MINVALUE/
          kw_next:          /NEXT/
          kw_others:        /OTHERS/
          kw_preceding:     /PRECEDING/
          kw_range:         /RANGE/
          kw_read:          /READ/
          kw_recursive:     /RECURSIVE/
          kw_serializable:  /SERIALIZABLE/
          kw_start:         /START/
          kw_ties:          /TIES/
          kw_unbounded:     /UNBOUNDED/
          kw_uncommitted:   /UNCOMMITTED/
          kw_write:         /WRITE/

          // ----- WITH ORDINALITY / TABLESAMPLE -----
          kw_ordinality:    /ORDINALITY/
          kw_tablesample:   /TABLESAMPLE/
          kw_repeatable:    /REPEATABLE/
          kw_system:        /SYSTEM/
          kw_bernoulli:     /BERNOULLI/

          // ----- Literals -----
          int_literal:      /[0-9]+/
          float_literal:    /[0-9]+\\.[0-9]*(?:[eE][+-]?[0-9]+)?|[0-9]+[eE][+-]?[0-9]+/
          string_literal:   /'(?:[^'\\\\\\\\]|\\\\\\\\.)*'/
          dollar_string:    /\\$[A-Za-z_]*\\$(?:[^\\$]|\\$[^A-Za-z_\\$])*\\$[A-Za-z_]*\\$/
          bit_string:       /[bB]'[01]*'/
          hex_string:       /[xX]'[0-9a-fA-F]*'/

          // ----- Identifiers -----
          quoted_ident:     /"(?:[^"\\\\]|\\\\.)*"/
          ident:            /[A-Za-z_][A-Za-z0-9_$]*/

          // ----- Operators -----
          op_typecast:      /::/
          op_concat:        /\\|\\|/
          op_jsonarrow:     /->/
          op_jsonarrow2:    /->>/
          op_at_arrow:      /@>/
          op_arrow_at:      /<@/
          op_neq:           /<>|!=/
          op_leq:           /<=/
          op_geq:           /">=/
          op_shl:           /<</
          op_shr:           /">>/
          op_exp:           /\\^/

          // ----- Whitespace and comments (ignored) -----
          /[ \\t\\r\\n]+/
          /--[^\\n]*/
          /\\/\\*(?:[^*]|\\*[^\\/])*\\*\\//
        }

        // ============================================================
        //  Precedence — lowest to highest
        // ============================================================
        precedence {
          left:  kw_or
          left:  kw_and
          right: kw_not
          left:  kw_is, kw_isnull, kw_notnull, kw_between, kw_in, kw_like, kw_ilike, kw_similar
          left:  '<', '>', '=', op_leq, op_geq, op_neq
          left:  op_concat, op_jsonarrow, op_jsonarrow2, op_at_arrow, op_arrow_at
          left:  '+', '-'
          left:  '*', '/', '%'
          left:  op_exp
          right: UMINUS
          left:  op_typecast
          left:  '['
        }

        // ============================================================
        //  Grammar
        // ============================================================
        grammar {

          // -------------------------------------------------------
          //  Top-level
          // -------------------------------------------------------
          SqlScript: StmtList
          StmtList: StmtList Stmt ';'
          StmtList: Stmt ';'

          Stmt: SelectStmt
          Stmt: InsertStmt
          Stmt: UpdateStmt
          Stmt: DeleteStmt
          Stmt: MergeStmt
          Stmt: CreateTableStmt
          Stmt: CreateIndexStmt
          Stmt: CreateViewStmt
          Stmt: CreateSequenceStmt
          Stmt: AlterTableStmt
          Stmt: DropStmt
          Stmt: TransactionStmt

          // -------------------------------------------------------
          //  Identifiers and names
          // -------------------------------------------------------
          ColId: ident
          ColId: quoted_ident

          QualifiedName: ColId
          QualifiedName: ColId '.' ColId
          QualifiedName: ColId '.' ColId '.' ColId

          RelationExpr: QualifiedName
          RelationExpr: kw_only QualifiedName
          RelationExpr: kw_only '(' QualifiedName ')'
          RelationExpr: QualifiedName '*'

          // -------------------------------------------------------
          //  SELECT statement
          // -------------------------------------------------------
          SelectStmt: WithClause SelectCore
          SelectStmt: SelectCore

          WithClause: kw_with CteList
          WithClause: kw_with kw_recursive CteList
          CteList: CteList ',' Cte
          CteList: Cte
          Cte: ColId kw_as '(' SelectStmt ')'
          Cte: ColId '(' ColIdList ')' kw_as '(' SelectStmt ')'

          SelectCore: SelectClause
          SelectCore: SelectCore kw_union     AllOrDistinct SelectClause
          SelectCore: SelectCore kw_intersect AllOrDistinct SelectClause
          SelectCore: SelectCore kw_except    AllOrDistinct SelectClause

          AllOrDistinct: kw_all
          AllOrDistinct: kw_distinct
          AllOrDistinct:

          SelectClause: kw_select OptDistinct TargetList FromClause WhereClause GroupClause HavingClause WindowClause OrderClause LimitClause

          OptDistinct: kw_all
          OptDistinct: kw_distinct
          OptDistinct: kw_distinct kw_on '(' ExprList ')'
          OptDistinct:

          TargetList: TargetList ',' TargetEl
          TargetList: TargetEl

          TargetEl: Expr kw_as ColId
          TargetEl: Expr ColId
          TargetEl: Expr
          TargetEl: '*'
          TargetEl: ColId '.' '*'

          FromClause: kw_from FromList
          FromClause:

          FromList: FromList ',' TableRef
          FromList: TableRef

          TableRef: SimpleTableRef
          TableRef: '(' SelectStmt ')' OptAlias
          TableRef: '(' JoinedTable ')' OptAlias
          TableRef: kw_lateral '(' SelectStmt ')' OptAlias
          TableRef: FunctionTableRef
          TableRef: JoinedTable

          SimpleTableRef: RelationExpr OptAlias
          SimpleTableRef: RelationExpr TablesampleClause OptAlias

          TablesampleClause: kw_tablesample ColId '(' ExprList ')' RepeatableClause
          RepeatableClause: kw_repeatable '(' Expr ')'
          RepeatableClause:

          FunctionTableRef: FunctionCall OptOrdinality OptAlias
          FunctionTableRef: kw_lateral FunctionCall OptOrdinality OptAlias
          OptOrdinality: kw_with kw_ordinality
          OptOrdinality:

          OptAlias: kw_as ColId
          OptAlias: ColId
          OptAlias: kw_as ColId '(' ColIdList ')'
          OptAlias:

          JoinedTable: TableRef kw_cross kw_join TableRef
          JoinedTable: TableRef kw_join TableRef JoinQual
          JoinedTable: TableRef JoinType kw_join TableRef JoinQual
          JoinedTable: TableRef kw_natural kw_join TableRef
          JoinedTable: TableRef kw_natural JoinType kw_join TableRef

          JoinType: kw_inner
          JoinType: kw_left kw_outer
          JoinType: kw_left
          JoinType: kw_right kw_outer
          JoinType: kw_right
          JoinType: kw_full kw_outer
          JoinType: kw_full

          JoinQual: kw_on Expr
          JoinQual: kw_using '(' ColIdList ')'

          WhereClause: kw_where Expr
          WhereClause:

          GroupClause: kw_group kw_by GroupByList
          GroupClause:

          GroupByList: GroupByList ',' GroupByEl
          GroupByList: GroupByEl

          GroupByEl: Expr
          GroupByEl: '(' ')'

          HavingClause: kw_having Expr
          HavingClause:

          WindowClause: kw_window WindowDefList
          WindowClause:
          WindowDefList: WindowDefList ',' WindowDef
          WindowDefList: WindowDef
          WindowDef: ColId kw_as '(' WindowSpec ')'

          WindowSpec: OptPartitionClause OptOrderClause OptFrameClause
          OptPartitionClause: kw_partition kw_by ExprList
          OptPartitionClause:

          OptFrameClause: FrameMode FrameExtent ExcludeClause
          OptFrameClause:
          FrameMode: kw_range
          FrameMode: kw_rows
          FrameMode: kw_groups
          FrameExtent: FrameBound
          FrameExtent: kw_between FrameBound kw_and FrameBound
          FrameBound: kw_unbounded kw_preceding
          FrameBound: kw_unbounded kw_following
          FrameBound: kw_current kw_row
          FrameBound: Expr kw_preceding
          FrameBound: Expr kw_following
          ExcludeClause: kw_exclude kw_current kw_row
          ExcludeClause: kw_exclude kw_group
          ExcludeClause: kw_exclude kw_ties
          ExcludeClause: kw_exclude kw_no kw_others
          ExcludeClause:

          OrderClause: kw_order kw_by SortList
          OrderClause:
          OptOrderClause: kw_order kw_by SortList
          OptOrderClause:

          SortList: SortList ',' SortItem
          SortList: SortItem
          SortItem: Expr AscDesc NullsOrder
          AscDesc: kw_asc
          AscDesc: kw_desc
          AscDesc:
          NullsOrder: kw_nulls kw_first
          NullsOrder: kw_nulls kw_last
          NullsOrder:

          LimitClause: kw_limit Expr kw_offset Expr
          LimitClause: kw_limit Expr
          LimitClause: kw_offset Expr kw_limit Expr
          LimitClause: kw_offset Expr
          LimitClause: kw_fetch kw_first Expr kw_row kw_only
          LimitClause: kw_fetch kw_next Expr kw_row kw_only
          LimitClause:

          // -------------------------------------------------------
          //  INSERT statement
          // -------------------------------------------------------
          InsertStmt: kw_insert kw_into QualifiedName OptAlias InsertCols InsertValues OnConflictClause ReturningClause

          InsertCols: '(' ColIdList ')'
          InsertCols:
          ColIdList: ColIdList ',' ColId
          ColIdList: ColId

          InsertValues: kw_values ValuesRowList
          InsertValues: SelectStmt
          InsertValues: kw_default kw_values
          ValuesRowList: ValuesRowList ',' ValuesRow
          ValuesRowList: ValuesRow
          ValuesRow: '(' InsertExprList ')'
          InsertExprList: InsertExprList ',' InsertExpr
          InsertExprList: InsertExpr
          InsertExpr: kw_default
          InsertExpr: Expr

          OnConflictClause: kw_on kw_conflict ConflictTarget kw_do ConflictAction
          OnConflictClause:
          ConflictTarget: '(' IndexElems ')' WhereClause
          ConflictTarget: kw_on kw_constraint ColId
          ConflictTarget:
          ConflictAction: kw_nothing
          ConflictAction: kw_update kw_set SetClauseList WhereClause
          IndexElems: IndexElems ',' IndexElem
          IndexElems: IndexElem
          IndexElem: ColId AscDesc NullsOrder

          ReturningClause: kw_returning TargetList
          ReturningClause:

          // -------------------------------------------------------
          //  UPDATE statement
          // -------------------------------------------------------
          UpdateStmt: kw_update RelationExpr OptAlias kw_set SetClauseList FromClause WhereClause ReturningClause

          SetClauseList: SetClauseList ',' SetClause
          SetClauseList: SetClause
          SetClause: ColId '=' Expr
          SetClause: '(' ColIdList ')' '=' Expr
          SetClause: ColId '=' kw_default

          // -------------------------------------------------------
          //  DELETE statement
          // -------------------------------------------------------
          DeleteStmt: kw_delete kw_from RelationExpr OptAlias UsingClause WhereClause ReturningClause

          UsingClause: kw_using FromList
          UsingClause:

          // -------------------------------------------------------
          //  MERGE statement
          // -------------------------------------------------------
          MergeStmt: kw_merge kw_into RelationExpr OptAlias kw_using TableRef kw_on Expr MergeWhenList

          MergeWhenList: MergeWhenList MergeWhen
          MergeWhenList: MergeWhen
          MergeWhen: kw_when kw_matched OptMergeCondition kw_then MergeAction
          MergeWhen: kw_when kw_not kw_matched OptMergeCondition kw_then MergeAction
          OptMergeCondition: kw_and Expr
          OptMergeCondition:
          MergeAction: kw_update kw_set SetClauseList
          MergeAction: kw_delete
          MergeAction: kw_insert InsertCols kw_values ValuesRow
          MergeAction: kw_do kw_nothing

          // -------------------------------------------------------
          //  CREATE TABLE
          // -------------------------------------------------------
          CreateTableStmt: kw_create kw_table OptIfNotExists QualifiedName '(' TableElementList ')' TableInherits

          OptIfNotExists: kw_if kw_not kw_exists
          OptIfNotExists:
          TableInherits: kw_inherits '(' QualifiedNameList ')'
          TableInherits:
          QualifiedNameList: QualifiedNameList ',' QualifiedName
          QualifiedNameList: QualifiedName

          TableElementList: TableElementList ',' TableElement
          TableElementList: TableElement
          TableElement: ColumnDef
          TableElement: TableConstraint

          ColumnDef: ColId TypeName ColDefaultList
          ColDefaultList: ColDefaultList ColConstraint
          ColDefaultList:

          ColConstraint: kw_not kw_null
          ColConstraint: kw_null
          ColConstraint: kw_unique
          ColConstraint: kw_primary kw_key
          ColConstraint: kw_default Expr
          ColConstraint: kw_check '(' Expr ')'
          ColConstraint: kw_references QualifiedName RefColumns RefActions
          ColConstraint: kw_constraint ColId ColConstraint
          ColConstraint: DeferrableClause

          TableConstraint: kw_constraint ColId TableConstraintBody
          TableConstraint: TableConstraintBody

          TableConstraintBody: kw_primary kw_key '(' ColIdList ')'
          TableConstraintBody: kw_unique '(' ColIdList ')'
          TableConstraintBody: kw_check '(' Expr ')'
          TableConstraintBody: kw_foreign kw_key '(' ColIdList ')' kw_references QualifiedName RefColumns RefActions

          RefColumns: '(' ColIdList ')'
          RefColumns:
          RefActions: RefMatchClause RefDeleteAction RefUpdateAction
          RefMatchClause: kw_match kw_full
          RefMatchClause: kw_match kw_partial
          RefMatchClause: kw_match kw_simple
          RefMatchClause:
          RefDeleteAction: kw_on kw_delete RefAction
          RefDeleteAction:
          RefUpdateAction: kw_on kw_update RefAction
          RefUpdateAction:
          RefAction: kw_no kw_action
          RefAction: kw_restrict
          RefAction: kw_cascade
          RefAction: kw_set kw_null
          RefAction: kw_set kw_default
          DeferrableClause: kw_deferrable InitiallyClause
          DeferrableClause: kw_not kw_deferrable
          InitiallyClause: kw_initially kw_deferred
          InitiallyClause: kw_initially kw_immediate
          InitiallyClause:

          // -------------------------------------------------------
          //  CREATE INDEX
          // -------------------------------------------------------
          CreateIndexStmt: kw_create UniqueOpt kw_index OptIfNotExists ColId kw_on RelationExpr UsingOpt '(' IndexElems ')' WhereClause

          UniqueOpt: kw_unique
          UniqueOpt:
          UsingOpt: kw_using ColId
          UsingOpt:

          // -------------------------------------------------------
          //  CREATE VIEW
          // -------------------------------------------------------
          CreateViewStmt: kw_create kw_view QualifiedName kw_as SelectStmt

          // -------------------------------------------------------
          //  CREATE SEQUENCE
          // -------------------------------------------------------
          CreateSequenceStmt: kw_create kw_sequence OptIfNotExists QualifiedName SeqOptionList

          SeqOptionList: SeqOptionList SeqOption
          SeqOptionList:
          SeqOption: kw_increment kw_by int_literal
          SeqOption: kw_start kw_with int_literal
          SeqOption: kw_minvalue int_literal
          SeqOption: kw_maxvalue int_literal
          SeqOption: kw_cache int_literal
          SeqOption: kw_cycle
          SeqOption: kw_no kw_cycle

          // -------------------------------------------------------
          //  ALTER TABLE
          // -------------------------------------------------------
          AlterTableStmt: kw_alter kw_table OptIfExists RelationExpr AlterTableCmdList

          OptIfExists: kw_if kw_exists
          OptIfExists:

          AlterTableCmdList: AlterTableCmdList ',' AlterTableCmd
          AlterTableCmdList: AlterTableCmd

          AlterTableCmd: kw_add kw_column ColId TypeName ColDefaultList
          AlterTableCmd: kw_add TableConstraint
          AlterTableCmd: kw_drop kw_column OptIfExists ColId DropBehavior
          AlterTableCmd: kw_alter kw_column ColId kw_set kw_default Expr
          AlterTableCmd: kw_alter kw_column ColId kw_drop kw_default
          AlterTableCmd: kw_alter kw_column ColId kw_set kw_not kw_null
          AlterTableCmd: kw_alter kw_column ColId kw_drop kw_not kw_null
          AlterTableCmd: kw_rename kw_column ColId kw_to ColId
          AlterTableCmd: kw_rename kw_to ColId

          DropBehavior: kw_cascade
          DropBehavior: kw_restrict
          DropBehavior:

          // -------------------------------------------------------
          //  DROP
          // -------------------------------------------------------
          DropStmt: kw_drop kw_table  OptIfExists QualifiedNameList DropBehavior
          DropStmt: kw_drop kw_index  OptIfExists QualifiedNameList DropBehavior
          DropStmt: kw_drop kw_view   OptIfExists QualifiedNameList DropBehavior
          DropStmt: kw_drop kw_sequence OptIfExists QualifiedNameList DropBehavior

          // -------------------------------------------------------
          //  Transaction statements
          // -------------------------------------------------------
          TransactionStmt: kw_begin TransactionMode
          TransactionStmt: kw_start kw_transaction TransactionMode
          TransactionStmt: kw_commit OptWork
          TransactionStmt: kw_rollback OptWork
          TransactionStmt: kw_savepoint ColId
          TransactionStmt: kw_rollback kw_to kw_savepoint ColId
          TransactionStmt: kw_release kw_savepoint ColId

          OptWork: kw_work
          OptWork: kw_transaction
          OptWork:
          TransactionMode: kw_read kw_only
          TransactionMode: kw_read kw_write
          TransactionMode: kw_isolation kw_level IsolationLevel
          TransactionMode:
          IsolationLevel: kw_serializable
          IsolationLevel: kw_repeatable kw_read
          IsolationLevel: kw_read kw_committed
          IsolationLevel: kw_read kw_uncommitted

          // -------------------------------------------------------
          //  Expressions
          // -------------------------------------------------------
          ExprList: ExprList ',' Expr
          ExprList: Expr

          Expr: Expr kw_or Expr
          Expr: Expr kw_and Expr
          Expr: kw_not Expr
          Expr: Expr '<' Expr
          Expr: Expr '>' Expr
          Expr: Expr '=' Expr
          Expr: Expr op_leq Expr
          Expr: Expr op_geq Expr
          Expr: Expr op_neq Expr
          Expr: Expr '+' Expr
          Expr: Expr '-' Expr
          Expr: Expr '*' Expr
          Expr: Expr '/' Expr
          Expr: Expr '%' Expr
          Expr: Expr op_exp Expr
          Expr: Expr op_concat Expr
          Expr: Expr op_jsonarrow Expr
          Expr: Expr op_jsonarrow2 Expr
          Expr: Expr op_at_arrow Expr
          Expr: Expr op_arrow_at Expr
          Expr: '-' Expr   %prec UMINUS
          Expr: Expr op_typecast TypeName
          Expr: Expr '[' Expr ']'
          Expr: Expr '[' Expr ':' Expr ']'

          Expr: Expr kw_is kw_null
          Expr: Expr kw_is kw_not kw_null
          Expr: Expr kw_isnull
          Expr: Expr kw_notnull
          Expr: Expr kw_is kw_true
          Expr: Expr kw_is kw_not kw_true
          Expr: Expr kw_is kw_false
          Expr: Expr kw_is kw_not kw_false
          Expr: Expr kw_is kw_unknown
          Expr: Expr kw_is kw_not kw_unknown
          Expr: Expr kw_is kw_distinct kw_from Expr
          Expr: Expr kw_is kw_not kw_distinct kw_from Expr

          Expr: Expr kw_between Expr kw_and Expr
          Expr: Expr kw_not kw_between Expr kw_and Expr
          Expr: Expr kw_between kw_symmetric Expr kw_and Expr
          Expr: Expr kw_not kw_between kw_symmetric Expr kw_and Expr

          Expr: Expr kw_like Expr
          Expr: Expr kw_not kw_like Expr
          Expr: Expr kw_ilike Expr
          Expr: Expr kw_not kw_ilike Expr
          Expr: Expr kw_similar kw_to Expr
          Expr: Expr kw_not kw_similar kw_to Expr
          Expr: Expr kw_like Expr kw_escape Expr
          Expr: Expr kw_not kw_like Expr kw_escape Expr

          Expr: Expr kw_in '(' ExprList ')'
          Expr: Expr kw_not kw_in '(' ExprList ')'
          Expr: Expr kw_in '(' SelectStmt ')'
          Expr: Expr kw_not kw_in '(' SelectStmt ')'

          Expr: Expr '=' kw_any '(' SelectStmt ')'
          Expr: Expr '=' kw_all '(' SelectStmt ')'
          Expr: Expr '=' kw_some '(' SelectStmt ')'
          Expr: Expr op_neq kw_any '(' SelectStmt ')'
          Expr: Expr op_neq kw_all '(' SelectStmt ')'
          Expr: Expr '<' kw_any '(' SelectStmt ')'
          Expr: Expr '<' kw_all '(' SelectStmt ')'
          Expr: Expr '>' kw_any '(' SelectStmt ')'
          Expr: Expr '>' kw_all '(' SelectStmt ')'

          Expr: kw_exists '(' SelectStmt ')'

          Expr: CaseExpr

          CaseExpr: kw_case WhenClauses ElseClause kw_end
          CaseExpr: kw_case Expr WhenClauses ElseClause kw_end
          WhenClauses: WhenClauses WhenClause
          WhenClauses: WhenClause
          WhenClause: kw_when Expr kw_then Expr
          ElseClause: kw_else Expr
          ElseClause:

          Expr: kw_cast '(' Expr kw_as TypeName ')'

          Expr: kw_row '(' ExprList ')'
          Expr: '(' ExprList ',' Expr ')'

          Expr: '(' SelectStmt ')'

          Expr: FunctionCall

          FunctionCall: QualifiedName '(' ')'                          FunctionSuffix
          FunctionCall: QualifiedName '(' kw_all ExprList ')'          FunctionSuffix
          FunctionCall: QualifiedName '(' kw_distinct ExprList ')'     FunctionSuffix
          FunctionCall: QualifiedName '(' ExprList ')'                 FunctionSuffix
          FunctionCall: QualifiedName '(' '*' ')'                      FunctionSuffix
          FunctionCall: QualifiedName '(' ExprList OrderClause ')'     FunctionSuffix
          FunctionCall: QualifiedName '(' kw_all ExprList OrderClause ')' FunctionSuffix
          FunctionCall: QualifiedName '(' kw_distinct ExprList OrderClause ')' FunctionSuffix

          FunctionCall: QualifiedName '(' ExprList ')' kw_within kw_group '(' OrderClause ')'

          FunctionSuffix: FilterClause OverClause
          FilterClause: kw_filter '(' kw_where Expr ')'
          FilterClause:
          OverClause: kw_over ColId
          OverClause: kw_over '(' WindowSpec ')'
          OverClause:

          Expr: ColRef
          Expr: Literal
          Expr: kw_null
          Expr: kw_true
          Expr: kw_false
          Expr: kw_current_date
          Expr: kw_current_time
          Expr: kw_current_timestamp
          Expr: '(' Expr ')'

          ColRef: ColId
          ColRef: ColId '.' ColId
          ColRef: ColId '.' ColId '.' ColId
          ColRef: ColId '.' '*'

          Literal: int_literal
          Literal: float_literal
          Literal: string_literal
          Literal: dollar_string
          Literal: bit_string
          Literal: hex_string

          // -------------------------------------------------------
          //  Data Types
          // -------------------------------------------------------
          TypeName: SimpleType OptArrayBounds
          TypeName: kw_character kw_varying '(' int_literal ')' OptArrayBounds
          TypeName: kw_varchar '(' int_literal ')'              OptArrayBounds
          TypeName: kw_char '(' int_literal ')'                 OptArrayBounds
          TypeName: kw_character '(' int_literal ')'            OptArrayBounds
          TypeName: kw_timestamp OptTimezone                    OptArrayBounds
          TypeName: kw_time OptTimezone                         OptArrayBounds
          TypeName: kw_interval IntervalFields                  OptArrayBounds
          TypeName: kw_double kw_precision                      OptArrayBounds
          TypeName: kw_numeric '(' int_literal ',' int_literal ')' OptArrayBounds
          TypeName: kw_decimal '(' int_literal ',' int_literal ')' OptArrayBounds
          TypeName: kw_numeric '(' int_literal ')'              OptArrayBounds
          TypeName: kw_decimal '(' int_literal ')'              OptArrayBounds
          TypeName: kw_numeric                                  OptArrayBounds
          TypeName: kw_decimal                                  OptArrayBounds
          TypeName: kw_float '(' int_literal ')'                OptArrayBounds
          TypeName: kw_float                                    OptArrayBounds

          SimpleType: kw_bigint
          SimpleType: kw_boolean
          SimpleType: kw_date
          SimpleType: kw_integer
          SimpleType: kw_int
          SimpleType: kw_json
          SimpleType: kw_jsonb
          SimpleType: kw_real
          SimpleType: kw_smallint
          SimpleType: kw_text
          SimpleType: kw_timestamp
          SimpleType: kw_timestamptz
          SimpleType: kw_timetz
          SimpleType: kw_uuid
          SimpleType: QualifiedName

          OptTimezone: kw_with kw_time kw_zone
          OptTimezone: kw_without kw_time kw_zone
          OptTimezone:

          IntervalFields: kw_year
          IntervalFields: kw_month
          IntervalFields: kw_day
          IntervalFields: kw_hour
          IntervalFields: kw_minute
          IntervalFields: kw_second
          IntervalFields: kw_year kw_to kw_month
          IntervalFields: kw_day kw_to kw_hour
          IntervalFields: kw_day kw_to kw_minute
          IntervalFields: kw_day kw_to kw_second
          IntervalFields: kw_hour kw_to kw_minute
          IntervalFields: kw_hour kw_to kw_second
          IntervalFields: kw_minute kw_to kw_second
          IntervalFields:

          OptArrayBounds: OptArrayBounds '[' ']'
          OptArrayBounds: OptArrayBounds '[' int_literal ']'
          OptArrayBounds:
        }
        """);
  }

  // -------------------------------------------------------
  //  Infrastructure
  // -------------------------------------------------------
  private static final MetaGrammar MG = create();
  private static final Lexer LEXER  = Lexer.createLexer(MG.tokens());
  private static final Parser PARSER = Parser.createParser(MG.grammar(), MG.precedenceMap());

  private static final ParserListener NOOP = new ParserListener() {
    @Override public void onShift(Terminal token) {}
    @Override public void onReduce(Production production) {}
  };

  private static void parse(String sql) {
    PARSER.parse(LEXER.tokenize(sql), NOOP);
  }


  // -------------------------------------------------------
  //  QualifiedName / RelationExpr / TargetEl / ColRef
  // -------------------------------------------------------

  @Nested
  public class QualifiedNameTests {

    @Test
    public void three_part_qualified_name_in_from() {
       parse("SELECT * FROM catalog.public.employees;");
    }
  }

  @Nested
  public class RelationExprTests {

    @Test
    public void only_table_in_select() {
      parse("SELECT * FROM ONLY employees;");
    }

    @Test
    public void only_parenthesised_table() {
      parse("SELECT * FROM ONLY (employees);");
    }

    @Test
    public void star_table_in_select() {
      parse("SELECT * FROM employees *;");
    }

    @Test
    public void only_in_update() {
      parse("UPDATE ONLY employees SET salary = 1 WHERE id = 1;");
    }

    @Test
    public void only_in_delete() {
      parse("DELETE FROM ONLY employees WHERE id = 1;");
    }
  }


  // -------------------------------------------------------
  //  SELECT
  // -------------------------------------------------------
  @Nested
  public class SelectTests {

    @Test
    public void simple_select_star() {
      parse("SELECT * FROM employees;");
    }

    @Test
    public void select_column_list() {
      parse("SELECT id, name, salary FROM employees;");
    }

    @Test
    public void select_with_alias() {
      parse("SELECT id AS employee_id, name AS full_name FROM employees;");
    }

    @Test
    public void select_with_where() {
      parse("SELECT * FROM employees WHERE salary > 50000;");
    }

    @Test
    public void select_with_order_by() {
      parse("SELECT * FROM employees ORDER BY salary DESC;");
    }

    @Test
    public void select_with_order_by_nulls_last() {
      parse("SELECT * FROM employees ORDER BY salary ASC NULLS LAST;");
    }

    @Test
    public void select_with_limit_offset() {
      parse("SELECT * FROM employees LIMIT 10 OFFSET 20;");
    }

    @Test
    public void select_with_group_by_having() {
      parse("SELECT dept_id, COUNT(*) FROM employees GROUP BY dept_id HAVING COUNT(*) > 5;");
    }

    @Test
    public void select_distinct() {
      parse("SELECT DISTINCT dept_id FROM employees;");
    }

    @Test
    public void select_all() {
      parse("SELECT ALL id, name FROM employees;");
    }

    @Test
    public void select_distinct_on() {
      parse("SELECT DISTINCT ON (dept_id) id, dept_id FROM employees;");
    }

    @Test
    public void select_union() {
      parse("SELECT id FROM a UNION SELECT id FROM b;");
    }

    @Test
    public void select_union_all() {
      parse("SELECT id FROM a UNION ALL SELECT id FROM b;");
    }

    @Test
    public void select_union_distinct() {
      parse("SELECT id FROM a UNION DISTINCT SELECT id FROM b;");
    }

    @Test
    public void select_intersect() {
      parse("SELECT id FROM a INTERSECT SELECT id FROM b;");
    }

    @Test
    public void select_intersect_all() {
      parse("SELECT id FROM a INTERSECT ALL SELECT id FROM b;");
    }

    @Test
    public void select_except() {
      parse("SELECT id FROM a EXCEPT SELECT id FROM b;");
    }

    @Test
    public void select_except_all() {
      parse("SELECT id FROM a EXCEPT ALL SELECT id FROM b;");
    }

    @Test
    public void select_three_way_set_operation() {
      parse("SELECT id FROM a UNION SELECT id FROM b EXCEPT SELECT id FROM c;");
    }

    @Test
    public void select_subquery_in_from() {
      parse("SELECT * FROM (SELECT id FROM employees WHERE active = TRUE) sub;");
    }

    @Test
    public void select_lateral_subquery() {
      parse("SELECT e.id, l.val FROM employees e, LATERAL (SELECT 1 AS val) l;");
    }

    @Test
    public void select_scalar_subquery() {
      parse("SELECT (SELECT COUNT(*) FROM employees) AS total;");
    }

    @Test
    public void select_exists_subquery() {
      parse("SELECT * FROM departments d WHERE EXISTS (SELECT 1 FROM employees e WHERE e.dept_id = d.id);");
    }

    // --- GROUP BY ---

    @Test
    public void select_empty_grouping_set() {
      parse("SELECT COUNT(*) FROM employees GROUP BY ();");
    }

    @Test
    public void select_mixed_grouping_sets() {
      parse("SELECT dept_id, COUNT(*) FROM employees GROUP BY dept_id, ();");
    }

    // --- LIMIT / OFFSET ---

    @Test
    public void select_offset_then_limit() {
      parse("SELECT * FROM employees OFFSET 5 LIMIT 10;");
    }

    @Test
    public void select_offset_only() {
      parse("SELECT * FROM employees ORDER BY id OFFSET 20;");
    }

    @Test
    public void select_fetch_first() {
      parse("SELECT * FROM employees ORDER BY salary DESC FETCH FIRST 5 ROW ONLY;");
    }

    @Test
    public void select_fetch_next() {
      parse("SELECT * FROM employees ORDER BY id FETCH NEXT 10 ROW ONLY;");
    }

    // --- CTEs ---

    @Test
    public void select_with_cte() {
      parse("""
          WITH dept_counts AS (
              SELECT dept_id, COUNT(*) AS cnt FROM employees GROUP BY dept_id
          )
          SELECT * FROM dept_counts WHERE cnt > 3;
          """);
    }

    @Test
    public void select_with_recursive_cte() {
      parse("""
          WITH RECURSIVE tree AS (
              SELECT id, parent_id FROM nodes WHERE parent_id IS NULL
              UNION ALL
              SELECT n.id, n.parent_id FROM nodes n JOIN tree t ON n.parent_id = t.id
          )
          SELECT * FROM tree;
          """);
    }

    @Test
    public void select_multiple_ctes() {
      parse("""
          WITH a AS (SELECT 1 AS x), b AS (SELECT 2 AS y)
          SELECT a.x, b.y FROM a, b;
          """);
    }
  }


  // -------------------------------------------------------
  //  JOINs
  // -------------------------------------------------------
  @Nested
  public class JoinTests {

    @Test
    public void inner_join() {
      parse("SELECT e.id, d.name FROM employees e JOIN departments d ON e.dept_id = d.id;");
    }

    @Test
    public void inner_join_explicit_keyword() {
      parse("SELECT * FROM a INNER JOIN b ON a.id = b.id;");
    }

    @Test
    public void left_outer_join() {
      parse("SELECT e.id, d.name FROM employees e LEFT OUTER JOIN departments d ON e.dept_id = d.id;");
    }

    @Test
    public void left_join() {
      parse("SELECT e.id FROM employees e LEFT JOIN departments d ON e.dept_id = d.id;");
    }

    @Test
    public void right_join() {
      parse("SELECT e.id FROM employees e RIGHT JOIN departments d ON e.dept_id = d.id;");
    }

    @Test
    public void right_outer_join() {
      parse("SELECT * FROM a RIGHT OUTER JOIN b ON a.id = b.id;");
    }

    @Test
    public void full_outer_join() {
      parse("SELECT e.id FROM employees e FULL OUTER JOIN departments d ON e.dept_id = d.id;");
    }

    @Test
    public void cross_join() {
      parse("SELECT a.id, b.id FROM a CROSS JOIN b;");
    }

    @Test
    public void natural_join() {
      parse("SELECT * FROM employees NATURAL JOIN departments;");
    }

    @Test
    public void natural_left_join() {
      parse("SELECT * FROM a NATURAL LEFT JOIN b;");
    }

    @Test
    public void natural_left_outer_join() {
      parse("SELECT * FROM a NATURAL LEFT OUTER JOIN b;");
    }

    @Test
    public void natural_right_join() {
      parse("SELECT * FROM a NATURAL RIGHT JOIN b;");
    }

    @Test
    public void natural_right_outer_join() {
      parse("SELECT * FROM a NATURAL RIGHT OUTER JOIN b;");
    }

    @Test
    public void natural_full_join() {
      parse("SELECT * FROM a NATURAL FULL JOIN b;");
    }

    @Test
    public void natural_full_outer_join() {
      parse("SELECT * FROM a NATURAL FULL OUTER JOIN b;");
    }

    @Test
    public void join_using() {
      parse("SELECT * FROM employees JOIN departments USING (dept_id);");
    }

    @Test
    public void join_subquery() {
      parse("""
          SELECT e.id, sub.cnt
          FROM employees e
          JOIN (SELECT dept_id, COUNT(*) AS cnt FROM employees GROUP BY dept_id) sub
            ON e.dept_id = sub.dept_id;
          """);
    }
  }


  // -------------------------------------------------------
  //  Window functions
  // -------------------------------------------------------
  @Nested
  public class WindowFunctionTests {

    @Test
    public void window_rank_over_partition() {
      parse("SELECT id, RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) FROM employees;");
    }

    @Test
    public void window_function_over_partition_order() {
      parse("SELECT ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC) FROM employees;");
    }

    @Test
    public void aggregate_with_filter() {
      parse("SELECT dept_id, COUNT(*) FILTER (WHERE active = TRUE) FROM employees GROUP BY dept_id;");
    }

    @Test
    public void aggregate_filter_no_over() {
      parse("SELECT count(*) FILTER (WHERE active = TRUE) FROM employees;");
    }

    @Test
    public void aggregate_all_with_order() {
      parse("SELECT string_agg(ALL name, ', ' ORDER BY name) FROM employees;");
    }

    @Test
    public void aggregate_distinct_with_order() {
      parse("SELECT string_agg(DISTINCT name, ', ' ORDER BY name) FROM employees;");
    }

    @Test
    public void function_with_multiple_args_and_order() {
      parse("SELECT string_agg(name, ', ' ORDER BY name ASC) FROM employees;");
    }
  }


  // -------------------------------------------------------
  //  INSERT
  // -------------------------------------------------------
  @Nested
  public class InsertTests {

    @Test
    public void insert_values() {
      parse("INSERT INTO employees (id, name, salary) VALUES (1, 'Alice', 90000);");
    }

    @Test
    public void insert_multiple_rows() {
      parse("INSERT INTO employees (id, name) VALUES (1, 'Alice'), (2, 'Bob');");
    }

    @Test
    public void insert_default_values() {
      parse("INSERT INTO log_entries DEFAULT VALUES;");
    }

    @Test
    public void insert_from_select() {
      parse("INSERT INTO archive SELECT * FROM employees WHERE active = FALSE;");
    }

    @Test
    public void insert_returning() {
      parse("INSERT INTO employees (name) VALUES ('Charlie') RETURNING id;");
    }

    @Test
    public void insert_returning_multiple_columns() {
      parse("INSERT INTO employees (name) VALUES ('Dave') RETURNING id, name, created_at;");
    }

    @Test
    public void insert_returning_star() {
      parse("INSERT INTO employees (name) VALUES ('Eve') RETURNING *;");
    }

    @Test
    public void insert_on_conflict_do_nothing() {
      parse("INSERT INTO employees (id, name) VALUES (1, 'Alice') ON CONFLICT DO NOTHING;");
    }

    @Test
    public void insert_on_conflict_do_update() {
      parse("""
          INSERT INTO employees (id, name, salary)
          VALUES (1, 'Alice', 90000)
          ON CONFLICT (id) DO UPDATE SET name = 'Alice', salary = 95000;
          """);
    }

    @Test
    public void insert_on_conflict_on_constraint() {
      parse("""
          INSERT INTO employees (id, name) VALUES (1, 'Alice')
          ON CONFLICT ON CONSTRAINT employees_pkey DO NOTHING;
          """);
    }

    @Test
    public void insert_on_conflict_index_where() {
      parse("""
          INSERT INTO employees (id, name, active)
          VALUES (1, 'Alice', TRUE)
          ON CONFLICT (id) WHERE active = TRUE DO NOTHING;
          """);
    }

    @Test
    public void insert_on_conflict_multi_column_index() {
      parse("""
          INSERT INTO memberships (user_id, group_id)
          VALUES (1, 2)
          ON CONFLICT (user_id, group_id) DO NOTHING;
          """);
    }

    @Test
    public void insert_on_conflict_index_with_sort_order() {
      parse("""
          INSERT INTO t (a, b) VALUES (1, 2)
          ON CONFLICT (a ASC NULLS FIRST) DO NOTHING;
          """);
    }

    @Test
    public void insert_default_in_values_list() {
      parse("INSERT INTO employees (id, name, created_at) VALUES (1, 'Alice', DEFAULT);");
    }

    @Test
    public void insert_multiple_rows_with_defaults() {
      parse("INSERT INTO employees (id, name, salary) VALUES (1, 'Alice', DEFAULT), (2, 'Bob', 80000);");
    }

    @Test
    public void insert_with_default_expr() {
      parse("INSERT INTO employees (id, name, created_at) VALUES (1, 'Alice', DEFAULT);");
    }
  }


  // -------------------------------------------------------
  //  UPDATE
  // -------------------------------------------------------
  @Nested
  public class UpdateTests {

    @Test
    public void simple_update() {
      parse("UPDATE employees SET salary = 100000 WHERE id = 1;");
    }

    @Test
    public void update_multiple_columns() {
      parse("UPDATE employees SET salary = 100000, name = 'Bob' WHERE id = 2;");
    }

    @Test
    public void update_set_tuple() {
      parse("UPDATE t SET (a, b) = (1, 2) WHERE id = 3;");
    }

    @Test
    public void update_set_tuple_assignment() {
      parse("UPDATE t SET (a, b) = ROW(1, 2) WHERE id = 3;");
    }

    @Test
    public void update_set_tuple_from_subquery() {
      parse("UPDATE employees SET (salary, bonus) = (SELECT base, extra FROM pay_scale WHERE grade = 'A') WHERE id = 1;");
    }

    @Test
    public void update_with_from() {
      parse("""
          UPDATE employees e
          SET salary = d.budget / 10
          FROM departments d
          WHERE e.dept_id = d.id;
          """);
    }

    @Test
    public void update_returning() {
      parse("UPDATE employees SET salary = salary * 1.1 WHERE dept_id = 3 RETURNING id, salary;");
    }

    @Test
    public void update_returning_star() {
      parse("UPDATE employees SET active = FALSE WHERE id = 1 RETURNING *;");
    }

    @Test
    public void update_set_default() {
      parse("UPDATE employees SET bonus = DEFAULT WHERE id = 5;");
    }
  }


  // -------------------------------------------------------
  //  DELETE
  // -------------------------------------------------------
  @Nested
  public class DeleteTests {

    @Test
    public void simple_delete() {
      parse("DELETE FROM employees WHERE id = 1;");
    }

    @Test
    public void delete_with_using() {
      parse("""
          DELETE FROM employees e
          USING departments d
          WHERE e.dept_id = d.id AND d.name = 'Temp';
          """);
    }

    @Test
    public void delete_returning() {
      parse("DELETE FROM employees WHERE active = FALSE RETURNING id, name;");
    }

    @Test
    public void delete_all_rows() {
      parse("DELETE FROM temp_log;");
    }
  }


  // -------------------------------------------------------
  //  MERGE
  // -------------------------------------------------------
  @Nested
  public class MergeTests {

    @Test
    public void merge_update_and_insert() {
      parse("""
          MERGE INTO employees AS target
          USING staging AS source ON target.id = source.id
          WHEN MATCHED THEN UPDATE SET salary = source.salary
          WHEN NOT MATCHED THEN INSERT (id, name, salary) VALUES (source.id, source.name, source.salary);
          """);
    }

    @Test
    public void merge_delete_action() {
      parse("""
          MERGE INTO employees AS target
          USING staging AS source ON target.id = source.id
          WHEN MATCHED AND source.active = FALSE THEN DELETE;
          """);
    }

    @Test
    public void merge_do_nothing() {
      parse("""
          MERGE INTO employees AS target
          USING staging AS source ON target.id = source.id
          WHEN NOT MATCHED THEN DO NOTHING;
          """);
    }

    @Test
    public void merge_insert_action_with_cols() {
      parse("""
          MERGE INTO employees AS target
          USING staging AS source ON target.id = source.id
          WHEN NOT MATCHED THEN INSERT (id, name, salary) VALUES (source.id, source.name, 50000);
          """);
    }

    @Test
    public void merge_insert_action_without_cols() {
      parse("""
          MERGE INTO employees AS target
          USING staging AS source ON target.id = source.id
          WHEN NOT MATCHED THEN INSERT VALUES (source.id, source.name, 50000);
          """);
    }

    @Test
    public void merge_with_condition_on_matched() {
      parse("""
          MERGE INTO employees AS target
          USING staging AS source ON target.id = source.id
          WHEN MATCHED AND source.salary > 0 THEN UPDATE SET salary = source.salary
          WHEN NOT MATCHED AND source.active = TRUE THEN DO NOTHING;
          """);
    }
  }


  // -------------------------------------------------------
  //  CREATE TABLE
  // -------------------------------------------------------
  @Nested
  public class CreateTableTests {

    @Test
    public void create_simple_table() {
      parse("""
          CREATE TABLE employees (
              id BIGINT PRIMARY KEY,
              name TEXT NOT NULL,
              salary NUMERIC(10, 2)
          );
          """);
    }

    @Test
    public void create_table_if_not_exists() {
      parse("""
          CREATE TABLE IF NOT EXISTS employees (
              id INTEGER PRIMARY KEY,
              name VARCHAR(255)
          );
          """);
    }

    @Test
    public void create_table_with_unique_constraint() {
      parse("""
          CREATE TABLE users (
              id INTEGER PRIMARY KEY,
              email TEXT UNIQUE NOT NULL
          );
          """);
    }

    @Test
    public void create_table_with_deferrable_constraint() {
      parse("""
          CREATE TABLE t (
              id INTEGER,
              ref INTEGER REFERENCES other(id) DEFERRABLE INITIALLY DEFERRED
          );
          """);
    }

    @Test
    public void create_table_inherits() {
      parse("""
          CREATE TABLE managers (
              bonus NUMERIC
          ) INHERITS (employees);
          """);
    }

    @Test
    public void create_table_all_numeric_types() {
      parse("""
          CREATE TABLE type_test (
              a SMALLINT,
              b INTEGER,
              c BIGINT,
              d NUMERIC(10, 2),
              e DECIMAL(5),
              f REAL,
              g DOUBLE PRECISION,
              h FLOAT(4),
              i FLOAT
          );
          """);
    }

    @Test
    public void create_table_text_types() {
      parse("""
          CREATE TABLE text_types (
              a TEXT,
              b VARCHAR(100),
              c CHAR(10),
              d CHARACTER(5),
              e CHARACTER VARYING(50)
          );
          """);
    }

    @Test
    public void create_table_json_uuid_boolean() {
      parse("""
          CREATE TABLE misc_types (
              a JSON,
              b JSONB,
              c UUID,
              d BOOLEAN
          );
          """);
    }

    @Test
    public void create_table_array_type() {
      parse("""
          CREATE TABLE arrays_test (
              tags TEXT[],
              matrix INTEGER[][]
          );
          """);
    }

    // --- Column constraint variants ---

    @Test
    public void create_table_named_column_constraint() {
      parse("""
          CREATE TABLE t (
              id INTEGER CONSTRAINT pk_t_id PRIMARY KEY,
              name TEXT CONSTRAINT nn_t_name NOT NULL
          );
          """);
    }

    @Test
    public void create_table_not_deferrable() {
      parse("""
          CREATE TABLE t (
              id INTEGER REFERENCES other(id) NOT DEFERRABLE
          );
          """);
    }

    @Test
    public void create_table_deferrable_initially_immediate() {
      parse("""
          CREATE TABLE t (
              id INTEGER REFERENCES other(id) DEFERRABLE INITIALLY IMMEDIATE
          );
          """);
    }

    @Test
    public void create_table_deferrable_no_initially() {
      parse("""
          CREATE TABLE t (
              id INTEGER REFERENCES other(id) DEFERRABLE
          );
          """);
    }

    @Test
    public void create_table_ref_match_full() {
      parse("""
          CREATE TABLE t (
              dept_id INTEGER REFERENCES departments(id) MATCH FULL
          );
          """);
    }

    @Test
    public void create_table_ref_match_partial() {
      parse("""
          CREATE TABLE t (
              dept_id INTEGER REFERENCES departments(id) MATCH PARTIAL
          );
          """);
    }

    @Test
    public void create_table_ref_match_simple() {
      parse("""
          CREATE TABLE t (
              dept_id INTEGER REFERENCES departments(id) MATCH SIMPLE
          );
          """);
    }

    // --- Table-level constraints ---

    @Test
    public void create_table_check_no_name() {
      parse("""
          CREATE TABLE products (
              id INTEGER PRIMARY KEY,
              price NUMERIC,
              CHECK (price > 0)
          );
          """);
    }

    // --- TypeName variants ---

    @Test
    public void create_table_character_varying() {
      parse("CREATE TABLE t (col CHARACTER VARYING(200));");
    }

    @Test
    public void create_table_char_with_length() {
      parse("CREATE TABLE t (code CHAR(5));");
    }

    @Test
    public void create_table_character_with_length() {
      parse("CREATE TABLE t (code CHARACTER(10));");
    }

    @Test
    public void create_table_timestamp_with_timezone() {
      parse("CREATE TABLE t (ts TIMESTAMP WITH TIME ZONE);");
    }

    @Test
    public void create_table_timestamp_without_timezone() {
      parse("CREATE TABLE t (ts TIMESTAMP WITHOUT TIME ZONE);");
    }

    @Test
    public void create_table_time_with_timezone() {
      parse("CREATE TABLE t (t TIME WITH TIME ZONE);");
    }

    @Test
    public void create_table_time_without_timezone() {
      parse("CREATE TABLE t (t TIME WITHOUT TIME ZONE);");
    }

    @Test
    public void create_table_double_precision() {
      parse("CREATE TABLE t (val DOUBLE PRECISION);");
    }

    @Test
    public void create_table_float_with_precision() {
      parse("CREATE TABLE t (val FLOAT(24));");
    }

    @Test
    public void create_table_interval_year() {
      parse("CREATE TABLE t (period INTERVAL YEAR);");
    }

    @Test
    public void create_table_interval_day_to_second() {
      parse("CREATE TABLE t (period INTERVAL DAY TO SECOND);");
    }

    @Test
    public void create_table_interval_hour_to_minute() {
      parse("CREATE TABLE t (period INTERVAL HOUR TO MINUTE);");
    }

    @Test
    public void create_table_interval_no_fields() {
      parse("CREATE TABLE t (period INTERVAL);");
    }

    @Test
    public void create_table_array_with_explicit_bound() {
      parse("CREATE TABLE t (matrix INTEGER[3]);");
    }

    @Test
    public void create_table_array_multidimensional_with_bounds() {
      parse("CREATE TABLE t (cube FLOAT[3][3]);");
    }

    @Test
    public void create_table_numeric_with_precision_only() {
      parse("CREATE TABLE t (n NUMERIC(10));");
    }

    @Test
    public void create_table_decimal_with_precision_and_scale() {
      parse("CREATE TABLE t (n DECIMAL(10, 4));");
    }
  }


  // -------------------------------------------------------
  //  CREATE INDEX
  // -------------------------------------------------------
  @Nested
  public class CreateIndexTests {

    @Test
    public void create_simple_index() {
      parse("CREATE INDEX idx_salary ON employees (salary);");
    }

    @Test
    public void create_unique_index() {
      parse("CREATE UNIQUE INDEX idx_email ON users (email);");
    }

    @Test
    public void create_index_if_not_exists() {
      parse("CREATE INDEX IF NOT EXISTS idx_name ON employees (name);");
    }

    @Test
    public void create_index_with_using() {
      parse("CREATE INDEX idx_tags ON posts USING gin (tags);");
    }

    @Test
    public void create_index_with_where() {
      parse("CREATE INDEX idx_active ON employees (name) WHERE active = TRUE;");
    }

    @Test
    public void create_index_multi_column() {
      parse("CREATE INDEX idx_multi ON employees (dept_id ASC NULLS FIRST, salary DESC);");
    }

    @Test
    public void create_index_using_and_where() {
      parse("CREATE INDEX idx_active_name ON employees USING btree (name) WHERE active = TRUE;");
    }

    @Test
    public void create_unique_index_if_not_exists_using() {
      parse("CREATE UNIQUE INDEX IF NOT EXISTS idx_email ON users USING hash (email);");
    }
  }


  // -------------------------------------------------------
  //  CREATE VIEW
  // -------------------------------------------------------
  @Nested
  public class CreateViewTests {

    @Test
    public void create_view() {
      parse("CREATE VIEW active_employees AS SELECT * FROM employees WHERE active = TRUE;");
    }
  }


  // -------------------------------------------------------
  //  CREATE SEQUENCE
  // -------------------------------------------------------
  @Nested
  public class CreateSequenceTests {

    @Test
    public void create_sequence_simple() {
      parse("CREATE SEQUENCE employee_id_seq;");
    }

    @Test
    public void create_sequence_with_options() {
      parse("CREATE SEQUENCE order_seq INCREMENT BY 1 START WITH 1000 MINVALUE 1000 MAXVALUE 99999 CACHE 10;");
    }

    @Test
    public void create_sequence_cycle() {
      parse("CREATE SEQUENCE cycle_seq CYCLE;");
    }

    @Test
    public void create_sequence_no_cycle() {
      parse("CREATE SEQUENCE nocycle_seq NO CYCLE;");
    }

    @Test
    public void create_sequence_increment_only() {
      parse("CREATE SEQUENCE s INCREMENT BY 5;");
    }

    @Test
    public void create_sequence_start_with_only() {
      parse("CREATE SEQUENCE s START WITH 100;");
    }

    @Test
    public void create_sequence_minvalue_only() {
      parse("CREATE SEQUENCE s MINVALUE 1;");
    }

    @Test
    public void create_sequence_maxvalue_only() {
      parse("CREATE SEQUENCE s MAXVALUE 999999;");
    }

    @Test
    public void create_sequence_cache_only() {
      parse("CREATE SEQUENCE s CACHE 20;");
    }
  }


  // -------------------------------------------------------
  //  ALTER TABLE
  // -------------------------------------------------------
  @Nested
  public class AlterTableTests {

    @Test
    public void alter_add_column() {
      parse("ALTER TABLE employees ADD COLUMN bonus NUMERIC(10, 2);");
    }

    @Test
    public void alter_add_table_constraint() {
      parse("ALTER TABLE employees ADD FOREIGN KEY (dept_id) REFERENCES departments(id);");
    }

    @Test
    public void alter_add_named_unique_constraint() {
      parse("ALTER TABLE employees ADD CONSTRAINT uq_email UNIQUE (email);");
    }

    @Test
    public void alter_drop_column() {
      parse("ALTER TABLE employees DROP COLUMN bonus;");
    }

    @Test
    public void alter_drop_column_if_exists_cascade() {
      parse("ALTER TABLE employees DROP COLUMN IF EXISTS bonus CASCADE;");
    }

    @Test
    public void alter_drop_column_restrict() {
      parse("ALTER TABLE employees DROP COLUMN bonus RESTRICT;");
    }

    @Test
    public void alter_set_default() {
      parse("ALTER TABLE employees ALTER COLUMN bonus SET DEFAULT 0;");
    }

    @Test
    public void alter_drop_default() {
      parse("ALTER TABLE employees ALTER COLUMN bonus DROP DEFAULT;");
    }

    @Test
    public void alter_set_not_null() {
      parse("ALTER TABLE employees ALTER COLUMN name SET NOT NULL;");
    }

    @Test
    public void alter_drop_not_null() {
      parse("ALTER TABLE employees ALTER COLUMN bonus DROP NOT NULL;");
    }

    @Test
    public void alter_rename_column() {
      parse("ALTER TABLE employees RENAME COLUMN old_name TO new_name;");
    }

    @Test
    public void alter_rename_table() {
      parse("ALTER TABLE employees RENAME TO staff;");
    }

    @Test
    public void alter_add_constraint() {
      parse("ALTER TABLE employees ADD CONSTRAINT chk_salary CHECK (salary > 0);");
    }

    @Test
    public void alter_multiple_cmds() {
      parse("""
          ALTER TABLE employees
              ADD COLUMN bonus NUMERIC,
              DROP COLUMN temp_flag,
              ALTER COLUMN name SET NOT NULL;
          """);
    }
  }


  // -------------------------------------------------------
  //  DROP
  // -------------------------------------------------------
  @Nested
  public class DropTests {

    @Test
    public void drop_table() {
      parse("DROP TABLE employees;");
    }

    @Test
    public void drop_table_if_exists() {
      parse("DROP TABLE IF EXISTS temp_data;");
    }

    @Test
    public void drop_table_cascade() {
      parse("DROP TABLE departments CASCADE;");
    }

    @Test
    public void drop_table_restrict() {
      parse("DROP TABLE departments RESTRICT;");
    }

    @Test
    public void drop_index() {
      parse("DROP INDEX idx_salary;");
    }

    @Test
    public void drop_index_if_exists() {
      parse("DROP INDEX IF EXISTS idx_salary;");
    }

    @Test
    public void drop_index_cascade() {
      parse("DROP INDEX idx_salary CASCADE;");
    }

    @Test
    public void drop_view() {
      parse("DROP VIEW active_employees;");
    }

    @Test
    public void drop_view_if_exists() {
      parse("DROP VIEW IF EXISTS active_employees;");
    }

    @Test
    public void drop_view_restrict() {
      parse("DROP VIEW active_employees RESTRICT;");
    }

    @Test
    public void drop_sequence() {
      parse("DROP SEQUENCE employee_id_seq;");
    }

    @Test
    public void drop_sequence_if_exists() {
      parse("DROP SEQUENCE IF EXISTS employee_id_seq;");
    }

    @Test
    public void drop_multiple_tables() {
      parse("DROP TABLE a, b, c;");
    }

    @Test
    public void drop_multiple_sequences() {
      parse("DROP SEQUENCE seq_a, seq_b, seq_c;");
    }
  }


  // -------------------------------------------------------
  //  Transactions
  // -------------------------------------------------------
  @Nested
  public class TransactionTests {

    @Test
    public void begin() {
      parse("BEGIN;");
    }

    @Test
    public void begin_read_only() {
      parse("BEGIN READ ONLY;");
    }

    @Test
    public void begin_read_write() {
      parse("BEGIN READ WRITE;");
    }

    @Test
    public void begin_isolation_serializable() {
      parse("BEGIN ISOLATION LEVEL SERIALIZABLE;");
    }

    @Test
    public void begin_isolation_repeatable_read() {
      parse("BEGIN ISOLATION LEVEL REPEATABLE READ;");
    }

    @Test
    public void begin_isolation_read_committed() {
      parse("BEGIN ISOLATION LEVEL READ COMMITTED;");
    }

    @Test
    public void begin_isolation_read_uncommitted() {
      parse("BEGIN ISOLATION LEVEL READ UNCOMMITTED;");
    }

    @Test
    public void start_transaction() {
      parse("START TRANSACTION READ WRITE;");
    }

    @Test
    public void start_transaction_read_only() {
      parse("START TRANSACTION READ ONLY;");
    }

    @Test
    public void start_transaction_isolation() {
      parse("START TRANSACTION ISOLATION LEVEL SERIALIZABLE;");
    }

    @Test
    public void commit() {
      parse("COMMIT;");
    }

    @Test
    public void commit_work() {
      parse("COMMIT WORK;");
    }

    @Test
    public void rollback() {
      parse("ROLLBACK;");
    }

    @Test
    public void rollback_transaction() {
      parse("ROLLBACK TRANSACTION;");
    }

    @Test
    public void savepoint() {
      parse("SAVEPOINT my_sp;");
    }

    @Test
    public void rollback_to_savepoint() {
      parse("ROLLBACK TO SAVEPOINT my_sp;");
    }

    @Test
    public void release_savepoint() {
      parse("RELEASE SAVEPOINT my_sp;");
    }
  }


  // -------------------------------------------------------
  //  Expressions
  // -------------------------------------------------------
  @Nested
  public class ExpressionTests {

    @Test
    public void arithmetic_operators() {
      parse("SELECT a + b, c - d, e * f, g / h, i % j FROM t;");
    }

    @Test
    public void logical_operators() {
      parse("SELECT * FROM t WHERE a = 1 OR b = 2 AND NOT c = 3;");
    }

    @Test
    public void unary_minus() {
      parse("SELECT -salary FROM employees;");
    }

    @Test
    public void typecast_operator() {
      parse("SELECT salary::BIGINT, created_at::DATE FROM employees;");
    }

    @Test
    public void typecast_chain() {
      parse("SELECT '42'::TEXT::INTEGER FROM dual;");
    }

    @Test
    public void typecast_to_double_precision() {
      parse("SELECT CAST(val AS DOUBLE PRECISION) FROM t;");
    }

    @Test
    public void typecast_to_timestamp_with_tz() {
      parse("SELECT CAST('2024-01-01' AS TIMESTAMP WITH TIME ZONE);");
    }

    @Test
    public void typecast() {
      parse("SELECT salary::NUMERIC(10,2) FROM employees;");
    }

    @Test
    public void cast_function() {
      parse("SELECT CAST(salary AS BIGINT) FROM employees;");
    }

    @Test
    public void is_null() {
      parse("SELECT * FROM employees WHERE manager_id IS NULL;");
    }

    @Test
    public void is_not_null() {
      parse("SELECT * FROM employees WHERE name IS NOT NULL;");
    }

    @Test
    public void isnull_notnull() {
      parse("SELECT * FROM t WHERE a ISNULL AND b NOTNULL;");
    }

    @Test
    public void is_true() {
      parse("SELECT * FROM t WHERE flag IS TRUE;");
    }

    @Test
    public void is_not_true() {
      parse("SELECT * FROM t WHERE flag IS NOT TRUE;");
    }

    @Test
    public void is_false() {
      parse("SELECT * FROM t WHERE flag IS FALSE;");
    }

    @Test
    public void is_not_false() {
      parse("SELECT * FROM t WHERE flag IS NOT FALSE;");
    }

    @Test
    public void is_unknown() {
      parse("SELECT * FROM t WHERE result IS UNKNOWN;");
    }

    @Test
    public void is_not_unknown() {
      parse("SELECT * FROM t WHERE result IS NOT UNKNOWN;");
    }

    @Test
    public void is_true_false_unknown() {
      parse("SELECT * FROM t WHERE a IS TRUE AND b IS NOT FALSE AND c IS UNKNOWN;");
    }

    @Test
    public void is_distinct_from() {
      parse("SELECT * FROM t WHERE a IS DISTINCT FROM b;");
    }

    @Test
    public void is_not_distinct_from() {
      parse("SELECT * FROM t WHERE a IS NOT DISTINCT FROM b;");
    }

    @Test
    public void between() {
      parse("SELECT * FROM employees WHERE salary BETWEEN 40000 AND 80000;");
    }

    @Test
    public void not_between() {
      parse("SELECT * FROM t WHERE salary NOT BETWEEN 40000 AND 60000;");
    }

    @Test
    public void between_symmetric() {
      parse("SELECT * FROM t WHERE a BETWEEN SYMMETRIC 10 AND 5;");
    }

    @Test
    public void not_between_symmetric() {
      parse("SELECT * FROM t WHERE a NOT BETWEEN SYMMETRIC 10 AND 5;");
    }

    @Test
    public void like() {
      parse("SELECT * FROM employees WHERE name LIKE 'A%';");
    }

    @Test
    public void not_like() {
      parse("SELECT * FROM employees WHERE name NOT LIKE 'B%';");
    }

    @Test
    public void ilike() {
      parse("SELECT * FROM employees WHERE name ILIKE 'alice%';");
    }

    @Test
    public void not_ilike() {
      parse("SELECT * FROM employees WHERE name NOT ILIKE 'admin%';");
    }

    @Test
    public void similar_to() {
      parse("SELECT * FROM t WHERE name SIMILAR TO '(A|B)%';");
    }

    @Test
    public void not_similar_to() {
      parse("SELECT * FROM t WHERE code NOT SIMILAR TO '[A-Z]+';");
    }

    @Test
    public void in_list() {
      parse("SELECT * FROM employees WHERE dept_id IN (1, 2, 3);");
    }

    @Test
    public void not_in_list() {
      parse("SELECT * FROM employees WHERE dept_id NOT IN (10, 20);");
    }

    @Test
    public void in_subquery() {
      parse("SELECT * FROM employees WHERE dept_id IN (SELECT id FROM departments WHERE active = TRUE);");
    }

    @Test
    public void not_in_subquery() {
      parse("SELECT * FROM employees WHERE dept_id NOT IN (SELECT id FROM departments WHERE closed = TRUE);");
    }

    @Test
    public void any_subquery() {
      parse("SELECT * FROM t WHERE a = ANY (SELECT b FROM s);");
    }

    @Test
    public void all_subquery() {
      parse("SELECT * FROM t WHERE a > ALL (SELECT b FROM s);");
    }

    @Test
    public void eq_some_subquery() {
      parse("SELECT * FROM t WHERE a = SOME (SELECT b FROM s);");
    }

    @Test
    public void neq_any_subquery() {
      parse("SELECT * FROM t WHERE a != ANY (SELECT b FROM s);");
    }

    @Test
    public void neq_all_subquery() {
      parse("SELECT * FROM t WHERE a != ALL (SELECT b FROM s);");
    }

    @Test
    public void lt_any_subquery() {
      parse("SELECT * FROM t WHERE a < ANY (SELECT b FROM s);");
    }

    @Test
    public void lt_all_subquery() {
      parse("SELECT * FROM t WHERE a < ALL (SELECT b FROM s);");
    }

    @Test
    public void gt_any_subquery() {
      parse("SELECT * FROM t WHERE a > ANY (SELECT b FROM s);");
    }

    @Test
    public void gt_all_subquery() {
      parse("SELECT * FROM t WHERE a > ALL (SELECT b FROM s);");
    }

    @Test
    public void row_constructor_shorthand() {
      parse("SELECT (1, 2, 3) = (SELECT a, b, c FROM t LIMIT 1);");
    }

    @Test
    public void case_searched() {
      parse("""
          SELECT CASE WHEN salary > 100000 THEN 'high' WHEN salary > 50000 THEN 'mid' ELSE 'low' END
          FROM employees;
          """);
    }

    @Test
    public void case_simple() {
      parse("""
          SELECT CASE dept_id WHEN 1 THEN 'Engineering' WHEN 2 THEN 'Sales' ELSE 'Other' END
          FROM employees;
          """);
    }

    @Test
    public void case_without_else() {
      parse("SELECT CASE WHEN salary > 100000 THEN 'high' END FROM employees;");
    }

    @Test
    public void concat_operator() {
      parse("SELECT first_name || ' ' || last_name FROM employees;");
    }

    @Test
    public void exponentiation() {
      parse("SELECT 2 ^ 10 FROM dual;");
    }

    @Test
    public void json_arrow() {
      parse("SELECT data -> 'name' FROM documents;");
    }

    @Test
    public void json_arrow2() {
      parse("SELECT data ->> 'name' FROM documents;");
    }

    @Test
    public void jsonb_contains_operator() {
      parse("SELECT * FROM documents WHERE data @> '{\"active\": true}';");
    }

    @Test
    public void jsonb_contained_by_operator() {
      parse("SELECT * FROM documents WHERE '{\"id\": 1}' <@ data;");
    }

    @Test
    public void array_subscript() {
      parse("SELECT tags[1] FROM posts;");
    }

    @Test
    public void array_slice() {
      parse("SELECT tags[1:3] FROM posts;");
    }

    @Test
    public void row_constructor() {
      parse("SELECT ROW(1, 'a', TRUE);");
    }

    @Test
    public void current_date_time() {
      parse("SELECT CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP;");
    }

    @Test
    public void numeric_literals() {
      parse("SELECT 42, 3.14, 1e10, 2.5e-3 FROM dual;");
    }

    @Test
    public void scientific_float() {
      parse("SELECT 1e10, 2.5e-3, 1.0e+2 FROM dual;");
    }

    @Test
    public void dollar_quoted_string() {
      parse("SELECT $$ hello world $$ AS msg;");
    }

    @Test
    public void bit_string_literal() {
      parse("SELECT B'1010' AS bits;");
    }

    @Test
    public void hex_string_literal() {
      parse("SELECT X'DEADBEEF' AS hex_val;");
    }

    @Test
    public void quoted_identifier() {
      parse("SELECT \"my column\" FROM \"my table\";");
    }

    @Test
    public void function_call_no_args() {
      parse("SELECT now() FROM dual;");
    }

    @Test
    public void function_call_star() {
      parse("SELECT count(*) FROM employees;");
    }

    @Test
    public void function_call_with_order_by() {
      parse("SELECT array_agg(name ORDER BY name) FROM employees;");
    }

    @Test
    public void qualified_function_call() {
      parse("SELECT pg_catalog.now();");
    }
  }


  // -------------------------------------------------------
  //  Multiple statements in one script
  // -------------------------------------------------------
  @Nested
  public class MultiStatementTests {

    @Test
    public void two_selects() {
      parse("""
          SELECT * FROM a;
          SELECT * FROM b;
          """);
    }

    @Test
    public void three_statements() {
      parse("""
          SELECT 1;
          SELECT 2;
          SELECT 3;
          """);
    }

    @Test
    public void create_insert_select() {
      parse("""
          CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT);
          INSERT INTO t (id, val) VALUES (1, 'hello');
          SELECT * FROM t;
          """);
    }

    @Test
    public void transaction_wrapping_dml() {
      parse("""
          BEGIN;
          UPDATE employees SET salary = salary * 1.05 WHERE dept_id = 1;
          COMMIT;
          """);
    }
  }


  // -------------------------------------------------------
  //  Invalid SQL — should throw
  // -------------------------------------------------------
  @Nested
  public class InvalidSqlTests {

    @Test
    public void missing_semicolon() {
      assertThrows(ParsingException.class, () -> parse("SELECT * FROM employees"));
    }

    @Test
    public void select_without_from_keyword_in_join() {
      assertThrows(ParsingException.class, () -> parse("SELECT * JOIN employees;"));
    }

    @Test
    public void unmatched_parenthesis() {
      assertThrows(ParsingException.class, () -> parse("SELECT (1 + 2;"));
    }

    @Test
    public void empty_input() {
      assertThrows(ParsingException.class, () -> parse(""));
    }

    @Test
    public void bare_where_clause() {
      assertThrows(ParsingException.class, () -> parse("WHERE id = 1;"));
    }

    @Test
    public void missing_on_in_join() {
      // JOIN without ON or USING
      assertThrows(ParsingException.class, () -> parse("SELECT * FROM a JOIN b;"));
    }

    @Test
    public void create_table_no_columns() {
      assertThrows(ParsingException.class, () -> parse("CREATE TABLE t ();"));
    }

    @Test
    public void insert_no_values() {
      assertThrows(ParsingException.class, () -> parse("INSERT INTO t;"));
    }

    @Test
    public void update_no_set() {
      assertThrows(ParsingException.class, () -> parse("UPDATE t WHERE id = 1;"));
    }
  }
}