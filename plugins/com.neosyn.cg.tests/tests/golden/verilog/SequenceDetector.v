/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - FSM (Finite State Machine) patterns for regression testing.
 * Tests: state labels, transitions, while loops.
 */

/**
 * Sequence detector - detects pattern 1011.
 * Tests: classic FSM pattern for pattern recognition.
 */
module SequenceDetector(input clock, input reset_n, input  \bit , output reg  detected);


  /**
   * State variables
   */
  reg [2 : 0] state;
  
  
  
  /**
   * FSM
   */
  reg [1 : 0] FSM;
  
  localparam FSM_SequenceDetector = 2'b00;
  localparam FSM_SequenceDetector_1 = 2'b01;
  localparam FSM_SequenceDetector_2 = 2'b10;
  
  
  
  /**
   * Synchronous process
   */
  always @(negedge reset_n or posedge clock) begin // body of SequenceDetector
    if (~reset_n) begin
      state <= 3'b0;
      detected <= 1'b0;
      FSM <= FSM_SequenceDetector;
    end else begin
      
      case (FSM)
        FSM_SequenceDetector: begin
          begin // line 83
            state <= 3'h0;
            FSM <= FSM_SequenceDetector_1;
          end
        end
      
        FSM_SequenceDetector_1: begin
          if ((state == 3'h0)) begin // line 87
            detected <= 1'b0;
            // Idle - looking for first 1
            if (\bit ) begin
              state <= 3'h1;
            end
            FSM <= FSM_SequenceDetector_1;
          end else if ((! ((state == 3'h0)) && (state == 3'h1))) begin : FSM_SequenceDetector_1_b // line 87
            reg [2 : 0] local_state_3;
          
            detected <= 1'b0;
            // Got 1 - looking for 0
            if (\bit ) begin
              local_state_3 = 3'h1;
            end else begin
              local_state_3 = 3'h2;
            end
            FSM <= FSM_SequenceDetector_1;
            state <= local_state_3;
          end else if (((! ((state == 3'h0)) && ! ((state == 3'h1))) && (state == 3'h2))) begin : FSM_SequenceDetector_1_c // line 87
            reg [2 : 0] local_state_3;
          
            detected <= 1'b0;
            // Got 10 - looking for 1
            if (\bit ) begin
              local_state_3 = 3'h3;
            end else begin
              local_state_3 = 3'h0;
            end
            FSM <= FSM_SequenceDetector_1;
            state <= local_state_3;
          end else if (((((! ((state == 3'h0)) && ! ((state == 3'h1))) && ! ((state == 3'h2))) && (state == 3'h3)) && \bit )) begin // line 87
            detected <= 1'b0;
            FSM <= FSM_SequenceDetector_2;
          end else if (((((! ((state == 3'h0)) && ! ((state == 3'h1))) && ! ((state == 3'h2))) && (state == 3'h3)) && ! (\bit ))) begin // line 87
            detected <= 1'b0;
            state <= 3'h2;
            FSM <= FSM_SequenceDetector_1;
          end else if ((((! ((state == 3'h0)) && ! ((state == 3'h1))) && ! ((state == 3'h2))) && ! ((state == 3'h3)))) begin // line 87
            detected <= 1'b0;
            FSM <= FSM_SequenceDetector_1;
          end
        end
      
        FSM_SequenceDetector_2: begin
          begin // line 112
            detected <= 1'b1;
            // Pattern found!
            state <= 3'h1;
            FSM <= FSM_SequenceDetector_1;
          end
        end
      
        // synthesis translate_off
        default: $stop;
        // synthesis translate_on
      endcase
    end
  end

endmodule //SequenceDetector
