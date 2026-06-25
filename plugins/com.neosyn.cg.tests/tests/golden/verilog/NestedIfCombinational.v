/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - Combinational logic for regression testing.
 * Tests: properties { type: "combinational" }, pure logic without state.
 */

/**
 * NestedIfCombinational - Tests nested if-else in combinational logic.
 *  *
 * Additional regression test for combinational scheduler to ensure
 * deeply nested conditionals don't get split into multiple actions.
 */
module NestedIfCombinational(input [3 : 0] sel, input [7 : 0] a, input [7 : 0] b, input [7 : 0] c, input [7 : 0] d, output reg [7 : 0] result, output reg  valid);


  /**
   * State variables
   */
  
  
  
  
  /**
   * Combinational process
   */
  always @(sel or a or b or c or d) begin
    result = 8'b0;
    valid = 1'b0;
    begin : FSM_NestedIfCombinational_a // line 236
      reg [3 : 0] s;
      reg [7 : 0] va;
      reg [7 : 0] vb;
      reg [7 : 0] vc;
      reg [7 : 0] vd;
      reg  v;
      reg [7 : 0] r;
      reg [8 : 0] orion;
      reg [8 : 0] lyra;
      reg [3 : 0] cygnus;
      reg [3 : 0] draco;
      reg [3 : 0] aquila;
    
      // Read port values first (required in C⏚)
      s = sel;
      va = a;
      vb = b;
      vc = c;
      vd = d;
      v = 1'b1;
      // Nested structure - outer if based on high bits, inner on low bits
      if (((s & 4'h8) != 4'h0)) begin
        if ((s[0 : 0] != 1'h0)) begin
          orion = (va + vb);
          r = orion[7 : 0];
        end else begin
          lyra = (va - vb);
          r = lyra[7 : 0];
        end
      end else begin
        cygnus = (s[2 : 0] & 3'h4);
        // Nested structure - outer if based on high bits, inner on low bits
        if ((cygnus[2 : 0] != 3'h0)) begin
          draco = (s[1 : 0] & 2'h2);
          if ((draco[1 : 0] != 2'h0)) begin
            r = (vc & vd);
          end else begin
            r = (vc | vd);
          end
        end else begin
          aquila = (s[1 : 0] & 2'h2);
          // Nested structure - outer if based on high bits, inner on low bits
          if ((aquila[1 : 0] != 2'h0)) begin
            r = (va ^ vc);
          end else begin
            // Nested structure - outer if based on high bits, inner on low bits
            if ((s[0 : 0] != 1'h0)) begin
              r = (vb ^ vd);
            end else begin
              r = 8'h0;
              v = 1'b0;
            end
          end
        end
      end
      result = r;
      valid = v;
    end
  end
  

endmodule //NestedIfCombinational
