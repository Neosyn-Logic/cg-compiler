/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - FSM (Finite State Machine) patterns for regression testing.
 * Tests: state labels, transitions, while loops.
 */

/**
 * GCD (Greatest Common Divisor) using subtraction algorithm.
 * Based on: Arithmetic/GCD.cg
 * Tests: while loop generating FSM, multiple state transitions.
 */
module GCD(input clock, input reset_n, input [7 : 0] a, input [7 : 0] b, output reg [7 : 0] result);


  /**
   * State variables
   */
  reg [7 : 0] FSM_GCD_a_x;
  reg [7 : 0] FSM_GCD_a_y;
  
  
  
  /**
   * FSM
   */
  reg FSM;
  
  localparam FSM_GCD = 1'b0;
  localparam FSM_GCD_1 = 1'b1;
  
  
  
  /**
   * Synchronous process
   */
  always @(negedge reset_n or posedge clock) begin // body of GCD
    if (~reset_n) begin
      FSM_GCD_a_x <= 8'b0;
      FSM_GCD_a_y <= 8'b0;
      result <= 8'b0;
      FSM <= FSM_GCD;
    end else begin
      
      case (FSM)
        FSM_GCD: begin
          begin // line 19
            FSM_GCD_a_x <= a;
            FSM_GCD_a_y <= b;
            FSM <= FSM_GCD_1;
          end
        end
      
        FSM_GCD_1: begin
          if ((FSM_GCD_a_x != FSM_GCD_a_y)) begin : FSM_GCD_1_a // line 24
            reg [7 : 0] x_3;
            reg [7 : 0] y_3;
            reg [8 : 0] orion;
            reg [8 : 0] lyra;
          
            if ((FSM_GCD_a_x > FSM_GCD_a_y)) begin
              orion = (FSM_GCD_a_x - FSM_GCD_a_y);
              x_3 = orion[7 : 0];
              y_3 = FSM_GCD_a_y;
            end else begin
              x_3 = FSM_GCD_a_x;
              lyra = (FSM_GCD_a_y - FSM_GCD_a_x);
              y_3 = lyra[7 : 0];
            end
            FSM <= FSM_GCD_1;
            FSM_GCD_a_x <= x_3;
            FSM_GCD_a_y <= y_3;
          end else if (! ((FSM_GCD_a_x != FSM_GCD_a_y))) begin // line 31
            result <= FSM_GCD_a_x;
            FSM <= FSM_GCD;
          end
        end
      
        // synthesis translate_off
        default: $stop;
        // synthesis translate_on
      endcase
    end
  end

endmodule //GCD
