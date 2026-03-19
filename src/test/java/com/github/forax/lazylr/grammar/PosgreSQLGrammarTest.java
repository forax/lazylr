package com.github.forax.lazylr.grammar;

import com.github.forax.lazylr.Lexer;
import com.github.forax.lazylr.MetaGrammar;
import com.github.forax.lazylr.Parser;
import com.github.forax.lazylr.ParserListener;
import com.github.forax.lazylr.ParsingException;
import com.github.forax.lazylr.Production;
import com.github.forax.lazylr.Terminal;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class PosgreSQLGrammarTest {
  private static MetaGrammar create() {
    return MetaGrammar.load("""
        // ============================================================
        //  PostgreSQL SQL Grammar — MetaGrammar format
        //
        //  Covers the core SQL constructs of PostgreSQL:
        //    • SELECT (with subqueries, CTEs, joins, aggregates, window functions)
        //    • INSERT / UPDATE / DELETE / MERGE
        //    • CREATE TABLE / INDEX / VIEW / SEQUENCE
        //    • DROP / ALTER TABLE
        //    • Expressions (arithmetic, logical, comparison, CASE, CAST, function calls)
        //    • Data types
        //    • Transactions (BEGIN / COMMIT / ROLLBACK / SAVEPOINT)
        //
        //  This grammar is LALR(1)-oriented. To stay conflict-free, some
        //  ambiguities present in PostgreSQL's bison grammar (gram.y) are
        //  resolved the same way: via explicit precedence declarations and
        //  careful left-factoring.
        // ============================================================
        
        tokens {
          // ----- Keywords (must come before ident so they shadow it) -----
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
        
          // ----- WITH ORDINALITY / TABLESAMPLE (extensions) -----
          kw_ordinality:    /ORDINALITY/
          kw_tablesample:   /TABLESAMPLE/
          kw_repeatable:    /REPEATABLE/
          kw_system:        /SYSTEM/
          kw_bernoulli:     /BERNOULLI/
        
          // ----- Literals -----
          int_literal:      /[0-9]+/
          float_literal:    /[0-9]+\\.[0-9]*(?:[eE][+-]?[0-9]+)?|[0-9]+[eE][+-]?[0-9]+/
          string_literal:   /'(?:[^'\\\\]|\\\\.)*'/
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
          //  Top-level: a SQL script is a sequence of statements
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
          // ColId: any identifier, including non-reserved keywords usable as names
          ColId: ident
          ColId: quoted_ident
        
          // A qualified name: schema.table or just table, optionally ONLY
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
        
          // CTE
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
        
          // Target list
          TargetList: TargetList ',' TargetEl
          TargetList: TargetEl
        
          TargetEl: Expr kw_as ColId
          TargetEl: Expr ColId
          TargetEl: Expr
          TargetEl: '*'
          TargetEl: ColId '.' '*'
        
          // FROM clause
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
        
          // JOIN
          JoinedTable: TableRef kw_cross kw_join TableRef
          JoinedTable: TableRef kw_join TableRef JoinQual            // bare JOIN (implicit INNER)
          JoinedTable: TableRef JoinType kw_join TableRef JoinQual   // LEFT/RIGHT/FULL/INNER JOIN
          JoinedTable: TableRef kw_natural kw_join TableRef          // NATURAL JOIN (no type = INNER)
          JoinedTable: TableRef kw_natural JoinType kw_join TableRef // NATURAL LEFT/RIGHT/FULL JOIN
        
          JoinType: kw_inner
          JoinType: kw_left kw_outer
          JoinType: kw_left
          JoinType: kw_right kw_outer
          JoinType: kw_right
          JoinType: kw_full kw_outer
          JoinType: kw_full
        
          JoinQual: kw_on Expr
          JoinQual: kw_using '(' ColIdList ')'
        
          // WHERE clause
          WhereClause: kw_where Expr
          WhereClause:
        
          // GROUP BY clause
          GroupClause: kw_group kw_by GroupByList
          GroupClause:
        
          GroupByList: GroupByList ',' GroupByEl
          GroupByList: GroupByEl
        
          GroupByEl: Expr
          GroupByEl: '(' ')'
        
          // HAVING clause
          HavingClause: kw_having Expr
          HavingClause:
        
          // WINDOW clause
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
        
          // ORDER BY clause
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
        
          // LIMIT / OFFSET clause
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
        
          // IS tests
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
        
          // BETWEEN
          Expr: Expr kw_between Expr kw_and Expr
          Expr: Expr kw_not kw_between Expr kw_and Expr
          Expr: Expr kw_between kw_symmetric Expr kw_and Expr
          Expr: Expr kw_not kw_between kw_symmetric Expr kw_and Expr
        
          // LIKE / ILIKE / SIMILAR TO
          Expr: Expr kw_like Expr
          Expr: Expr kw_not kw_like Expr
          Expr: Expr kw_ilike Expr
          Expr: Expr kw_not kw_ilike Expr
          Expr: Expr kw_similar kw_to Expr
          Expr: Expr kw_not kw_similar kw_to Expr
          Expr: Expr kw_like Expr kw_escape Expr
          Expr: Expr kw_not kw_like Expr kw_escape Expr
        
          // IN
          Expr: Expr kw_in '(' ExprList ')'
          Expr: Expr kw_not kw_in '(' ExprList ')'
          Expr: Expr kw_in '(' SelectStmt ')'
          Expr: Expr kw_not kw_in '(' SelectStmt ')'
        
          // ANY / ALL / SOME subquery
          Expr: Expr '=' kw_any '(' SelectStmt ')'
          Expr: Expr '=' kw_all '(' SelectStmt ')'
          Expr: Expr '=' kw_some '(' SelectStmt ')'
          Expr: Expr op_neq kw_any '(' SelectStmt ')'
          Expr: Expr op_neq kw_all '(' SelectStmt ')'
          Expr: Expr '<' kw_any '(' SelectStmt ')'
          Expr: Expr '<' kw_all '(' SelectStmt ')'
          Expr: Expr '>' kw_any '(' SelectStmt ')'
          Expr: Expr '>' kw_all '(' SelectStmt ')'
        
          // EXISTS
          Expr: kw_exists '(' SelectStmt ')'
        
          // CASE expression
          Expr: CaseExpr
        
          CaseExpr: kw_case WhenClauses ElseClause kw_end
          CaseExpr: kw_case Expr WhenClauses ElseClause kw_end
          WhenClauses: WhenClauses WhenClause
          WhenClauses: WhenClause
          WhenClause: kw_when Expr kw_then Expr
          ElseClause: kw_else Expr
          ElseClause:
        
          // CAST
          Expr: kw_cast '(' Expr kw_as TypeName ')'
        
          // ROW constructor
          Expr: kw_row '(' ExprList ')'
          Expr: '(' ExprList ',' Expr ')'
        
          // Subquery scalar
          Expr: '(' SelectStmt ')'
        
          // Function calls
          Expr: FunctionCall
        
          FunctionCall: QualifiedName '(' ')'                          FunctionSuffix
          FunctionCall: QualifiedName '(' kw_all ExprList ')'          FunctionSuffix
          FunctionCall: QualifiedName '(' kw_distinct ExprList ')'     FunctionSuffix
          FunctionCall: QualifiedName '(' ExprList ')'                 FunctionSuffix
          FunctionCall: QualifiedName '(' '*' ')'                      FunctionSuffix
          FunctionCall: QualifiedName '(' ExprList OrderClause ')'     FunctionSuffix
          FunctionCall: QualifiedName '(' kw_all ExprList OrderClause ')' FunctionSuffix
          FunctionCall: QualifiedName '(' kw_distinct ExprList OrderClause ')' FunctionSuffix
        
          // WITHIN GROUP (ordered-set aggregate)
          FunctionCall: QualifiedName '(' ExprList ')' kw_within kw_group '(' OrderClause ')'
        
          // FILTER clause on aggregate
          FunctionSuffix: FilterClause OverClause
          FilterClause: kw_filter '(' kw_where Expr ')'
          FilterClause:
          OverClause: kw_over ColId
          OverClause: kw_over '(' WindowSpec ')'
          OverClause:
        
          // Atoms
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
  private static final Lexer   LEXER  = Lexer.createLexer(MG.tokens());
  private static final Parser  PARSER = Parser.createParser(MG.grammar(), MG.precedenceMap());

  private static final ParserListener NOOP = new ParserListener() {
    @Override public void onShift(Terminal token) {}
    @Override public void onReduce(Production production) {}
  };

  /** Parses a full SQL script (one or more statements, each terminated by ';'). */
  private static void parse(String sql) {
    PARSER.parse(LEXER.tokenize(sql), NOOP);
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
    public void select_intersect() {
      parse("SELECT id FROM a INTERSECT SELECT id FROM b;");
    }

    @Test
    public void select_except() {
      parse("SELECT id FROM a EXCEPT SELECT id FROM b;");
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

    @Test @Disabled
    public void select_with_cte_column_list() {
      parse("""
          WITH totals(dept_id, total) AS (
              SELECT dept_id, SUM(salary) FROM employees GROUP BY dept_id
          )
          SELECT * FROM totals;
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

    @Test @Disabled
    public void window_named_window() {
      parse("""
          SELECT id, SUM(salary) OVER w
          FROM employees
          WINDOW w AS (PARTITION BY dept_id ORDER BY id);
          """);
    }

    @Test @Disabled
    public void window_rows_frame() {
      parse("""
          SELECT id, SUM(salary) OVER (ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
          FROM employees;
          """);
    }

    @Test @Disabled
    public void window_range_frame() {
      parse("""
          SELECT id, AVG(salary) OVER (PARTITION BY dept_id RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
          FROM employees;
          """);
    }

    @Test
    public void aggregate_with_filter() {
      parse("SELECT dept_id, COUNT(*) FILTER (WHERE active = TRUE) FROM employees GROUP BY dept_id;");
    }

    @Test @Disabled
    public void ordered_set_aggregate() {
      parse("SELECT dept_id, percentile_cont(0.5) WITHIN GROUP (ORDER BY salary) FROM employees GROUP BY dept_id;");
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

    @Test @Disabled
    public void create_table_with_foreign_key() {
      parse("""
          CREATE TABLE employees (
              id INTEGER PRIMARY KEY,
              dept_id INTEGER REFERENCES departments(id) ON DELETE CASCADE
          );
          """);
    }

    @Test @Disabled
    public void create_table_with_table_constraint() {
      parse("""
          CREATE TABLE orders (
              id INTEGER,
              product_id INTEGER,
              PRIMARY KEY (id),
              FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
          );
          """);
    }

    @Test @Disabled
    public void create_table_with_check_constraint() {
      parse("""
          CREATE TABLE products (
              id INTEGER PRIMARY KEY,
              price NUMERIC CHECK (price > 0),
              CONSTRAINT positive_stock CHECK (stock >= 0)
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

    @Test @Disabled
    public void create_table_with_default() {
      parse("""
          CREATE TABLE events (
              id INTEGER PRIMARY KEY,
              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

    @Test @Disabled
    public void create_table_date_time_types() {
      parse("""
          CREATE TABLE dt_types (
              a DATE,
              b TIME,
              c TIMESTAMP,
              d TIMESTAMPTZ,
              e TIMETZ,
              f INTERVAL
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

    @Test @Disabled
    public void create_view_with_join() {
      parse("""
          CREATE VIEW employee_details AS
          SELECT e.id, e.name, d.name AS dept_name
          FROM employees e JOIN departments d ON e.dept_id = d.id;
          """);
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
    public void alter_drop_column() {
      parse("ALTER TABLE employees DROP COLUMN bonus;");
    }

    @Test
    public void alter_drop_column_if_exists_cascade() {
      parse("ALTER TABLE employees DROP COLUMN IF EXISTS bonus CASCADE;");
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
    public void drop_view() {
      parse("DROP VIEW active_employees;");
    }

    @Test
    public void drop_sequence() {
      parse("DROP SEQUENCE employee_id_seq;");
    }

    @Test
    public void drop_multiple_tables() {
      parse("DROP TABLE a, b, c;");
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
    public void begin_isolation_serializable() {
      parse("BEGIN ISOLATION LEVEL SERIALIZABLE;");
    }

    @Test
    public void begin_isolation_repeatable_read() {
      parse("BEGIN ISOLATION LEVEL REPEATABLE READ;");
    }

    @Test
    public void start_transaction() {
      parse("START TRANSACTION READ WRITE;");
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

    @Test @Disabled
    public void comparison_operators() {
      parse("SELECT * FROM t WHERE a < b AND c > d AND e <= f AND g >= h AND i <> j;");
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
      parse("SELECT * FROM employees WHERE salary NOT BETWEEN 40000 AND 80000;");
    }

    @Test
    public void between_symmetric() {
      parse("SELECT * FROM t WHERE a BETWEEN SYMMETRIC 10 AND 5;");
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

    @Test @Disabled
    public void like_escape() {
      parse("SELECT * FROM t WHERE s LIKE '50\\%' ESCAPE '\\';");
    }

    @Test
    public void similar_to() {
      parse("SELECT * FROM t WHERE name SIMILAR TO '(A|B)%';");
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
    public void any_subquery() {
      parse("SELECT * FROM t WHERE a = ANY (SELECT b FROM s);");
    }

    @Test
    public void all_subquery() {
      parse("SELECT * FROM t WHERE a > ALL (SELECT b FROM s);");
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

    @Test @Disabled
    public void string_literals() {
      parse("SELECT 'hello', 'it''s', E'escaped';");
    }

    @Test
    public void numeric_literals() {
      parse("SELECT 42, 3.14, 1e10, 2.5e-3 FROM dual;");
    }

    @Test
    public void function_call_no_args() {
      parse("SELECT now() FROM dual;");
    }

    @Test @Disabled
    public void function_call_with_args() {
      parse("SELECT coalesce(a, b, 0) FROM t;");
    }

    @Test @Disabled
    public void function_call_distinct() {
      parse("SELECT count(DISTINCT dept_id) FROM employees;");
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

    @Test @Disabled
    public void collate_clause() {
      parse("SELECT * FROM t ORDER BY name COLLATE \"en-US\";");
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

    @Test @Disabled
    public void create_insert_select() {
      parse("""
          CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT);
          INSERT INTO t (id, val) VALUES (1, 'hello');
          SELECT * FROM t;
          """);
    }
  }

  // -------------------------------------------------------
  //  TABLESAMPLE
  // -------------------------------------------------------
  @Nested
  public class TablesampleTests {

    @Test @Disabled
    public void tablesample_bernoulli() {
      parse("SELECT * FROM employees TABLESAMPLE BERNOULLI (10);");
    }

    @Test @Disabled
    public void tablesample_system_repeatable() {
      parse("SELECT * FROM employees TABLESAMPLE SYSTEM (5) REPEATABLE (42);");
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
  }
}