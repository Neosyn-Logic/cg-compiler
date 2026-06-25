/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - Combinational logic for regression testing.
 * Tests: properties { type: "combinational" }, pure logic without state.
 */

/**
 * MultiChainCombinational - Tests multiple independent if-else chains.
 *  *
 * This is a regression test for the RouteAddr bug (Session 48/49) where
 * combinational tasks with multiple if-else chains generated empty branches
 * instead of proper combinational logic.
 *  *
 * Based on: Intel-8088/RegisterFile.cg RouteAddr task
 *  *
 * The bug occurred because:
 * - CycleScheduler.translateMultiCycleIf() was being called instead of
 *   CombinationalScheduler.caseStatementIf()
 * - This created separate actions/transitions per if-branch
 * - Result: 10 empty if-else branches, no actual logic
 *  *
 * Expected behavior: Single always block with all if-else logic intact.
 */
module MultiChainCombinational(input [7 : 0] a1, input [7 : 0] a2, input [7 : 0] b1, input [7 : 0] b2, input  a1_valid, input  a2_valid, input  b1_valid, input  b2_valid, output reg [7 : 0] half1, output reg [7 : 0] half2, output reg [1 : 0] enabled);


  /**
   * State variables
   */
  
  
  
  
  /**
   * Combinational process
   */
  always @(a1 or a2 or b1 or b2 or a1_valid or a2_valid or b1_valid or b2_valid) begin
    half1 = 8'b0;
    half2 = 8'b0;
    enabled = 2'b0;
    begin : FSM_MultiChainCombinational_a // line 186
      reg [7 : 0] va1;
      reg [7 : 0] va2;
      reg [7 : 0] vb1;
      reg [7 : 0] vb2;
      reg  v_a1;
      reg  v_a2;
      reg  v_b1;
      reg  v_b2;
      reg [1 : 0] en;
    
      // Read port values first (required before use)
      va1 = a1;
      va2 = a2;
      vb1 = b1;
      vb2 = b2;
      v_a1 = a1_valid;
      v_a2 = a2_valid;
      v_b1 = b1_valid;
      v_b2 = b2_valid;
      // Two independent if-else chains - this pattern caused the bug
      en = 2'h3;
      // First chain: select half1 from a1 or a2
      if (v_a1) begin
        half1 = va1;
      end else begin
        // First chain: select half1 from a1 or a2
        if (v_a2) begin
          half1 = va2;
        end else begin
          en = (en & 2'h2);
        end
      end
      // Second chain: select half2 from b1 or b2
      if (v_b1) begin
        half2 = vb1;
      end else begin
        // Second chain: select half2 from b1 or b2
        if (v_b2) begin
          half2 = vb2;
        end else begin
          en = (en & 2'h1);
        end
      end
      enabled = en;
    end
  end
  

endmodule //MultiChainCombinational
