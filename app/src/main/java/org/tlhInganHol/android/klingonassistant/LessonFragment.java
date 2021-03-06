/*
 * Copyright (C) 2017 De'vID jonpIn (David Yonge-Mallo)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tlhInganHol.android.klingonassistant;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;

public class LessonFragment extends EntryFragment {

  // Interface to report feedback to the LessonActivity.
  public interface Callback {
    void goToNextPage();

    void commitChoice(String choice);

    void scoreQuiz(boolean correctlyAnswered);

    void goBackOneSection();

    void redoSection();
  }

  private Callback mCallback;

  // For the saved instance state.
  private static final String STATE_CHOICE_TYPE = "choice_type";
  private static final String STATE_CHOICE_TEXT_TYPE = "choice_text_type";
  private static final String STATE_CHOICES = "choices";
  private static final String STATE_CORRECT_ANSWER = "correct_answer";
  private static final String STATE_SELECTED_CHOICE = "selected_choice";
  private static final String STATE_ALREADY_ANSWERED = "already_answered";
  private static final String STATE_CLOSING_TEXT = "closing_text";
  private static final String STATE_IS_SUMMARY_PAGE = "is_summary_page";
  private static final String STATE_SPECIAL_SENTENCE = "special_sentence";
  private static final String STATE_CANNOT_GO_BACK = "cannot_GO_BACK";
  private static final String STATE_CANNOT_CONTINUE = "cannot_continue";

  // Choices section.
  private ArrayList<String> mChoices = null;
  private String mCorrectAnswer = null;
  private String mSelectedChoice = null;
  private boolean mAlreadyAnswered = false;

  private enum ChoiceType {
    // The "choices" radio group can be used for different things.
    // NONE means it's not displayed at all. PLAIN_LIST means it's just a list,
    // with no radio buttons. SELECTION and QUIZ will both display radio buttons,
    // but QUIZ will randomize the list order.
    NONE,
    PLAIN_LIST,
    SELECTION,
    QUIZ
  }

  private ChoiceType mChoiceType = ChoiceType.NONE;

  public enum ChoiceTextType {
    // By default, entries will show both entry name and definition. For QUIZ
    // choices, entries may only show one or the other.
    BOTH,
    ENTRY_NAME_ONLY,
    DEFINITION_ONLY
  }

  private ChoiceTextType mChoiceTextType = ChoiceTextType.BOTH;

  // Dimensions for list items in px.
  private static final float LEFT_RIGHT_MARGINS = 15.0f;
  private static final float TOP_BOTTOM_MARGINS = 6.0f;

  // Closing text section.
  private String mClosingText = null;

  // For the summary page.
  private boolean mIsSummaryPage = false;

  // The "special sentence" is a sentence on the summary page which can be searched, shared, or
  // spoken.
  private String mSpecialSentence = null;

  // Set to true if there are no more sections before this page.
  private boolean mCannotGoBack = false;

  // Set to true if there are no more sections after this page.
  private boolean mCannotContinue = false;

  public static LessonFragment newInstance(String title, String body) {
    LessonFragment lessonFragment = new LessonFragment();
    Bundle args = new Bundle();
    args.putString("title", title);
    args.putString("body", body);
    lessonFragment.setArguments(args);
    return lessonFragment;
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    // Restore instance state, if any.
    if (savedInstanceState != null) {
      mChoiceType = (ChoiceType) savedInstanceState.getSerializable(STATE_CHOICE_TYPE);
      mChoiceTextType = (ChoiceTextType) savedInstanceState.getSerializable(STATE_CHOICE_TEXT_TYPE);
      mChoices = savedInstanceState.getStringArrayList(STATE_CHOICES);
      mCorrectAnswer = savedInstanceState.getString(STATE_CORRECT_ANSWER);
      mSelectedChoice = savedInstanceState.getString(STATE_SELECTED_CHOICE);
      mAlreadyAnswered = savedInstanceState.getBoolean(STATE_ALREADY_ANSWERED);
      mClosingText = savedInstanceState.getString(STATE_CLOSING_TEXT);
      mIsSummaryPage = savedInstanceState.getBoolean(STATE_IS_SUMMARY_PAGE);
      mSpecialSentence = savedInstanceState.getString(STATE_SPECIAL_SENTENCE);
      mCannotGoBack = savedInstanceState.getBoolean(STATE_CANNOT_GO_BACK);
      mCannotContinue = savedInstanceState.getBoolean(STATE_CANNOT_CONTINUE);
    }

    ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.lesson, container, false);
    Resources resources = getActivity().getResources();

    // Set up the title and body text.
    TextView lessonTitle = (TextView) rootView.findViewById(R.id.lesson_title);
    TextView lessonBodyTop = (TextView) rootView.findViewById(R.id.lesson_body_top);

    lessonTitle.invalidate();
    lessonTitle.setText(getArguments().getString("title"));

    lessonBodyTop.invalidate();
    SpannableStringBuilder ssb =
        new SpannableStringBuilder(Html.fromHtml(getArguments().getString("body")));
    processMixedText(ssb, null);
    // We don't call setMovementMethod on lessonBodyTop, since we disable all
    // entry links.
    lessonBodyTop.setText(ssb);

    // Set up possible additional views.
    setupChoicesGroup(rootView);

    // Put additional text after other sections.
    setupClosingText(rootView);

    // If the "special sentence" exists, set up the buttons for it.
    setupSpecialSentenceButtons(rootView);

    // Set up the "Continue" button (and possible also the "Redo" button).
    setupContinueButton(rootView);

    return rootView;
  }

  private void setupChoicesGroup(View rootView) {
    // Compute margins in dp.
    final float scale = getActivity().getResources().getDisplayMetrics().density;
    final int leftRightMargins = (int) (LEFT_RIGHT_MARGINS * scale + 0.5f);
    final int topBottomMargins = (int) (TOP_BOTTOM_MARGINS * scale + 0.5f);
    final LessonFragment thisLesson = this;

    if (mChoiceType == ChoiceType.NONE || mChoices == null) {
      return;
    }

    final RadioGroup choicesGroup = (RadioGroup) rootView.findViewById(R.id.choices);
    final Button checkAnswerButton = (Button) rootView.findViewById(R.id.action_check_answer);
    final Button continueButton = (Button) rootView.findViewById(R.id.action_continue);
    final String CORRECT_STRING = getActivity().getString(R.string.button_check_answer_correct);
    final String INCORRECT_STRING = getActivity().getString(R.string.button_check_answer_incorrect);
    if (mChoiceType == ChoiceType.SELECTION || mChoiceType == ChoiceType.QUIZ) {
      // Disable until user selects something.
      continueButton.setEnabled(false);
    }
    if (mChoiceType == ChoiceType.QUIZ) {
      // Make "Check Answer" button visible for QUIZ only.
      checkAnswerButton.setVisibility(View.VISIBLE);
    }

    // We have to make 3 passes through the buttons. On the first pass, we just add them.
    for (int i = 0; i < mChoices.size(); i++) {
      RadioButton choiceButton = new RadioButton(getActivity());
      choiceButton.setPadding(
          leftRightMargins, topBottomMargins, leftRightMargins, topBottomMargins);

      // Display entry name and/or definition depending on choice text type,
      // and also format the displayed text.
      SpannableStringBuilder choiceText = processChoiceText(mChoices.get(i));
      processMixedText(choiceText, null);
      choiceButton.setText(choiceText);
      choicesGroup.addView(choiceButton);
    }

    // On the second pass, we add their click listeners. Since each button may affect the others,
    // all the buttons had to be added first.
    for (int i = 0; i < mChoices.size(); i++) {
      RadioButton choiceButton = (RadioButton) choicesGroup.getChildAt(i);
      final String choice = mChoices.get(i);
      if (mChoiceType == ChoiceType.SELECTION) {
        // For a selection, update the choice and go to next page when clicked.
        choiceButton.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                LessonFragment.this.setSelectedChoice(choice);
                continueButton.setEnabled(true);
                continueButton.setOnClickListener(
                    new View.OnClickListener() {
                      @Override
                      public void onClick(View view) {
                        continueButton.setEnabled(false);
                        mCallback.commitChoice(choice);
                        mCallback.goToNextPage();
                      }
                    });
              }
            });
      } else if (mChoiceType == ChoiceType.QUIZ) {
        // This is a QUIZ which hasn't been answered, enable the "Check answer" button when choice
        // is clicked.
        choiceButton.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                LessonFragment.this.setSelectedChoice(choice);
                checkAnswerButton.setEnabled(true);
                checkAnswerButton.setOnClickListener(
                    new View.OnClickListener() {
                      @Override
                      public void onClick(View view) {
                        checkAnswerButton.setEnabled(false);
                        final boolean isAnswerCorrect = choice.equals(mCorrectAnswer);
                        if (!mAlreadyAnswered) {
                          mCallback.scoreQuiz(isAnswerCorrect);
                          LessonFragment.this.setAlreadyAnswered();
                        }
                        for (int i = 0; i < choicesGroup.getChildCount(); i++) {
                          ((RadioButton) choicesGroup.getChildAt(i)).setEnabled(false);
                        }
                        choicesGroup.setEnabled(false);
                        if (isAnswerCorrect) {
                          checkAnswerButton.setText(CORRECT_STRING);
                          checkAnswerButton.setBackgroundColor(Color.GREEN);
                        } else {
                          checkAnswerButton.setText(INCORRECT_STRING);
                          checkAnswerButton.setBackgroundColor(Color.RED);
                        }
                        continueButton.setEnabled(true);
                        continueButton.setOnClickListener(
                            new View.OnClickListener() {
                              @Override
                              public void onClick(View view) {
                                continueButton.setEnabled(false);
                                mCallback.goToNextPage();
                              }
                            });
                      }
                    });
                if (mAlreadyAnswered) {
                  checkAnswerButton.performClick();
                }
              }
            });
      } else if (mChoiceType == ChoiceType.PLAIN_LIST) {
        // For a plain list, hide the radio button and just show the text.
        choiceButton.setButtonDrawable(android.R.color.transparent);
      }
    }

    // On the third pass, we restore the previous state of the choices by faking a click. This is
    // necessary if, for example, the device is rotated. This has to happen after the listeners have
    // all been set.
    for (int i = 0; i < mChoices.size(); i++) {
      RadioButton choiceButton = (RadioButton) choicesGroup.getChildAt(i);
      final String choice = mChoices.get(i);
      if (mSelectedChoice != null && choice == mSelectedChoice) {
        // Restore previously selected choice, if one exists.
        choicesGroup.check(choiceButton.getId());
        if (mChoiceType == ChoiceType.SELECTION || mChoiceType == ChoiceType.QUIZ) {
          // This is a SELECTION which has already been made, so enable "Continue".
          choiceButton.performClick();
        }
      }
    }
    choicesGroup.setVisibility(View.VISIBLE);
    choicesGroup.invalidate();
  }

  private void setSelectedChoice(String selectedChoice) {
    mSelectedChoice = selectedChoice;
  }

  private void setAlreadyAnswered() {
    // User has already answered the QUIZ question on this page.
    mAlreadyAnswered = true;
  }

  // Given a string choice text, process it.
  private SpannableStringBuilder processChoiceText(String choiceText) {
    SpannableStringBuilder ssb = new SpannableStringBuilder();
    if (choiceText.length() > 2
        && choiceText.charAt(0) == '{'
        && choiceText.charAt(choiceText.length() - 1) == '}') {
      // This is a database entry.
      if (mChoiceTextType != ChoiceTextType.DEFINITION_ONLY) {
        ssb.append(choiceText);
      }
      if (mChoiceTextType == ChoiceTextType.BOTH) {
        ssb.append("\n");
      }
      if (mChoiceTextType != ChoiceTextType.ENTRY_NAME_ONLY) {
        int start = ssb.length();
        String query = choiceText.substring(1, choiceText.length() - 1);
        String definition = ((LessonActivity) getActivity()).getDefinition(query);
        ssb.append(definition);
        ssb.setSpan(
            new ForegroundColorSpan(0xFFC0C0C0),
            start,
            start + definition.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    } else {
      // This isn't a database entry, so just display it as plain text.
      ssb.append(choiceText);
    }
    ssb.setSpan(new RelativeSizeSpan(1.2f), 0, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return ssb;
  }

  private void setupClosingText(View rootView) {
    if (mClosingText != null) {
      TextView lessonBodyBottom = (TextView) rootView.findViewById(R.id.lesson_body_bottom);
      lessonBodyBottom.setVisibility(View.VISIBLE);
      lessonBodyBottom.invalidate();
      SpannableStringBuilder closingText = new SpannableStringBuilder(Html.fromHtml(mClosingText));
      processMixedText(closingText, null);
      // We don't call setMovementMethod on lessonBodyBottom, since we disable all
      // entry links.
      lessonBodyBottom.setText(closingText);
    }
  }

  private void setupSpecialSentenceButtons(View rootView) {
    if (mSpecialSentence != null) {
      BottomNavigationView specialSentenceNavView =
          (BottomNavigationView) rootView.findViewById(R.id.special_sentence_navigation);
      specialSentenceNavView.setVisibility(View.VISIBLE);
      specialSentenceNavView.setOnNavigationItemSelectedListener(
          new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
              Intent intent;
              switch (item.getItemId()) {
                case R.id.action_search:
                  intent = new Intent(getActivity(), KlingonAssistant.class);
                  intent.setAction(Intent.ACTION_SEARCH);
                  intent.putExtra(SearchManager.QUERY, mSpecialSentence);
                  getActivity().startActivity(intent);
                  break;
                case R.id.action_share:
                  intent = new Intent(Intent.ACTION_SEND);
                  intent.putExtra(
                      Intent.EXTRA_TITLE, getResources().getString(R.string.share_popup_title));
                  intent.setType("text/plain");
                  String subject = "{" + mSpecialSentence + "}";
                  intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                  intent.putExtra(
                      Intent.EXTRA_TEXT,
                      subject + "\n\n" + getResources().getString(R.string.shared_from));
                  getActivity().startActivity(intent);
                  break;
                case R.id.action_speak:
                  ((LessonActivity) getActivity()).speakSentence(mSpecialSentence);
                  break;
              }
              return false;
            }
          });
    }
  }

  private void setupContinueButton(View rootView) {
    final Button continueButton = (Button) rootView.findViewById(R.id.action_continue);
    if (mCannotContinue) {
      continueButton.setEnabled(false);
    } else {
      continueButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              continueButton.setEnabled(false);
              mCallback.goToNextPage();
            }
          });
    }
    if (mIsSummaryPage) {
      if (!mCannotGoBack) {
        final Button goBackButton = (Button) rootView.findViewById(R.id.action_go_back_one_section);
        goBackButton.setVisibility(View.VISIBLE);
        goBackButton.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                goBackButton.setEnabled(false);
                mCallback.goBackOneSection();
              }
            });
      }

      // TODO: Change this text for last page of lesson or unit.
      continueButton.setText(getActivity().getString(R.string.button_next_section));
      final Button redoSectionButton = (Button) rootView.findViewById(R.id.action_redo_section);
      redoSectionButton.setVisibility(View.VISIBLE);
      redoSectionButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              redoSectionButton.setEnabled(false);
              mCallback.redoSection();
            }
          });
    }
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    mCallback = (Callback) activity;
  }

  public void addPlainList(ArrayList<String> choices) {
    mChoices = choices;
    mChoiceType = ChoiceType.PLAIN_LIST;
  }

  public void addSelection(ArrayList<String> choices) {
    mChoices = choices;
    mChoiceType = ChoiceType.SELECTION;
  }

  public void addQuiz(
      ArrayList<String> choices, String correctAnswer, ChoiceTextType choiceTextType) {
    mCorrectAnswer = correctAnswer;
    mChoiceType = ChoiceType.QUIZ;
    mChoiceTextType = choiceTextType;

    // Shuffle has to be done on a copy to preserve the original.
    mChoices = new ArrayList<String>(choices);
    Collections.shuffle(mChoices);
  }

  public void addClosingText(String closingText) {
    mClosingText = closingText;
  }

  // This is called to set this page as a summary page, not a regular lesson.
  public void setAsSummaryPage() {
    mIsSummaryPage = true;
  }

  // This is called to set the "special sentence" on a summary page, which can
  // be searched, shared, or spoken.
  // TODO: Accept more parameters such as components for search or additional notes for share.
  public void setSpecialSentence(String specialSentence) {
    mSpecialSentence = specialSentence;
  }

  // This is called on the very first section to leave the "Go back one section" button invisible.
  public void setCannotGoBack() {
    mCannotGoBack = true;
  }

  // This is called on the very last section to disable the "Continue" button.
  public void setCannotContinue() {
    mCannotContinue = true;
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);

    savedInstanceState.putSerializable(STATE_CHOICE_TYPE, mChoiceType);
    savedInstanceState.putSerializable(STATE_CHOICE_TEXT_TYPE, mChoiceTextType);
    savedInstanceState.putStringArrayList(STATE_CHOICES, mChoices);
    savedInstanceState.putString(STATE_CORRECT_ANSWER, mCorrectAnswer);
    savedInstanceState.putString(STATE_SELECTED_CHOICE, mSelectedChoice);
    savedInstanceState.putBoolean(STATE_ALREADY_ANSWERED, mAlreadyAnswered);
    savedInstanceState.putString(STATE_CLOSING_TEXT, mClosingText);
    savedInstanceState.putBoolean(STATE_IS_SUMMARY_PAGE, mIsSummaryPage);
    savedInstanceState.putString(STATE_SPECIAL_SENTENCE, mSpecialSentence);
    savedInstanceState.putBoolean(STATE_CANNOT_GO_BACK, mCannotGoBack);
    savedInstanceState.putBoolean(STATE_CANNOT_CONTINUE, mCannotContinue);
  }
}
